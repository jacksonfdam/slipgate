# Slipgate — foundation specification

Slipgate is a Kotlin Multiplatform application that presents a console-style game select
screen and boots classic id Tech 1 engine games, each compiled to WebAssembly and executed
through a shared host shell.

Root package: `com.jacksonfdam.slipgate`. Original v1 targets were Android and Web
(wasmJs); [addendum 01](02-addendum-01.md) adds iOS.

## 1. Prior art and attribution

The project is directly inspired by [mood](https://github.com/CharlieTap/mood), a Kotlin
Multiplatform Doom implementation that runs the original C compiled to WebAssembly through
[Chasm](https://github.com/CharlieTap/chasm). Slipgate generalises that idea from a single
game to a multi-game launcher.

The README must credit mood prominently, near the top. No source files are copied from
mood; the architecture is studied and reimplemented. Any snippet that does get used is
attributed in a `NOTICE` file against the upstream commit, under mood's dual MIT / Apache
2.0 terms.

## 2. What Slipgate is

A launcher first, an engine host second.

- **Boot** — animated title and attract screen.
- **Select screen** — a horizontally scrolling rack of gates, each with cover art, engine
  name, and availability state: installed, needs game data, or locked.
- **Gate transition** — a shader-driven warp effect, after which the game takes the surface.
- **In-game** — virtual controls on touch platforms or keyboard and pointer lock on web,
  plus an overlay menu for settings, save management and returning to the launcher.

v1 ships three gates, all Chocolate Doom derived:

| Gate | Engine | Module | Free data available |
|---|---|---|---|
| `mars` | Doom | `:games:mars` | Yes — Freedoom |
| `corvus` | Heretic | `:games:corvus` | Yes — Blasphemer |
| `korax` | Hexen | `:games:korax` | No — user-supplied IWAD only |

Quake is out of scope for v1. The plugin contract stays capable of hosting a non-Doom
engine — no Doom assumptions in the host interfaces — but no attempt is made at it.

## 3. Module layout

```
slipgate/
├── host/
│   ├── runtime/          com.jacksonfdam.slipgate.host.runtime
│   ├── controls/         com.jacksonfdam.slipgate.host.controls
│   └── graphics/
│       ├── core/         com.jacksonfdam.slipgate.host.graphics.core
│       ├── backend/webgpu
│       ├── backend/classic
│       ├── upscaler/fsr1
│       └── effect/crt
├── launcher/             com.jacksonfdam.slipgate.launcher
├── ui/                   com.jacksonfdam.slipgate.ui
├── games/
│   ├── mars/             com.jacksonfdam.slipgate.games.mars
│   ├── corvus/           com.jacksonfdam.slipgate.games.corvus
│   └── korax/            com.jacksonfdam.slipgate.games.korax
├── android/
├── web/
└── tooling/
    └── engine-build/     (scripts, not a Gradle module)
```

Hard rule: **`host/*` must not depend on `games/*`**. The dependency arrow points one way.
`launcher` discovers gates through a registry populated by the platform entry point.

## 4. The gate contract

The most important design work in the project. It is settled before any game-specific code
is written, and proven with Doom as the only consumer before Heretic is added.

Defined in `host/runtime/src/commonMain`:

```kotlin
interface Gate {
    val descriptor: GateDescriptor
    fun requirements(): DataRequirements
    suspend fun createSession(data: MountedGameData, host: GateHost): GateSession
}

data class GateDescriptor(
    val id: GateId,
    val title: String,
    val engine: String,
    val artwork: GateArtwork,
    val accent: AccentSource,   // static, or derived from the game's own palette
)

interface GateSession {
    val display: DisplayFormat        // dimensions + pixel format (indexed8, rgba8, ...)
    fun palette(): IntArray?          // null for non-indexed engines
    fun step(input: InputFrame, elapsedMillis: Long): FrameResult
    fun snapshot(): ByteArray         // for suspend/resume
    fun close()
}

interface GateHost {
    val audio: AudioSink
    val storage: SaveStorage          // multi-slot, multi-file (Hexen hubs need this)
    val logger: Logger
    val clock: Clock
}
```

`Gate.createSession` is superseded by the backend axis in
[addendum 01 §C](02-addendum-01.md#c--the-backend-axis).

Nothing here may assume 320×200, 8-bit indexed colour, 35 Hz tics, or a single save blob.
Those are properties of a session, not of the host — which is what keeps a future Quake gate
viable.

Input is a normalised `InputFrame`: movement axes, look axes, an action bitset, plus an
engine-specific extension map for things like Heretic's inventory cycling. Each gate
declares an `InputProfile` so the virtual gamepad can lay out the right buttons; Doom needs
fewer than Hexen.

## 5. Engine to WebAssembly pipeline

### 5.1 Fetching Chocolate Doom

Chocolate Doom carries Doom, Heretic, Hexen and Strife in one tree behind a shared platform
abstraction, which is why all three v1 gates come from a single upstream source.

`tooling/engine-build/fetch-sources.sh` clones a pinned tag:

```bash
#!/usr/bin/env bash
set -euo pipefail

CHOCOLATE_DOOM_REPO="https://github.com/chocolate-doom/chocolate-doom.git"
CHOCOLATE_DOOM_REF="${CHOCOLATE_DOOM_REF:?set to the tag you verified}"

WORKDIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="${WORKDIR}/.sources/chocolate-doom"

rm -rf "${SRC_DIR}"
git clone --depth 1 --branch "${CHOCOLATE_DOOM_REF}" "${CHOCOLATE_DOOM_REPO}" "${SRC_DIR}"
git -C "${SRC_DIR}" rev-parse HEAD > "${WORKDIR}/SOURCES.lock.tmp"
```

Requirements:

- The pinned tag is verified against the upstream releases page before use.
- `SOURCES.lock` records repo URL, tag, resolved commit SHA, and the SHA-256 of every
  produced `.wasm` artifact. CI verifies these match.
- `.sources/` is gitignored. The engine tree is never vendored.
- The script is idempotent and runs on macOS and on Ubuntu CI runners.

Relevant upstream layout: `src/doom/`, `src/heretic/`, `src/hexen/` for per-game code;
`src/` for shared code including the `i_*` platform layer; `src/i_video.c`, `i_sound.c`,
`i_input.c`, `i_timer.c`, `i_system.c`, `i_joystick.c` for the SDL2 implementations being
replaced; `textscreen/`, `opl/`, `pcsound/` for subsystems, most of which can be stubbed for
v1.

### 5.2 The wasm platform layer

Building upstream with Emscripten's SDL2 port is the wrong path: it targets browser APIs
directly, will not run under Chasm's interpreter on Android, and drags in Asyncify, which is
a performance disaster on an interpreter.

Instead the Chocolate Doom tree stays pristine and an out-of-tree platform layer in
`tooling/engine-build/platform/` provides its own implementations of the `i_*` interfaces.
Chocolate Doom's SDL usage is largely confined to those files, which is what makes the port
tractable.

Per engine:

1. **Replace the platform layer.** `i_video` writes into a linear framebuffer in wasm linear
   memory and exports its address; `i_sound` renders PCM into a ring buffer the host drains;
   `i_input` reads from a host-written event queue; `i_timer` and `i_system` call imported
   host functions.
2. **Invert the main loop.** `D_DoomLoop` and its Heretic and Hexen equivalents never
   return. Split them into an init call plus a per-frame step. If the pinned revision
   already exposes a `D_RunFrame`-style entry point, export that instead of writing a new
   split.
3. **Stub music initially.** SFX only. OPL emulation is a later change, gated behind a
   measured performance budget.
4. **Virtual filesystem.** WAD loading goes through WASI preview 1, which Chasm supports via
   `wasi-emscripten-host`. The user's selected game data is mounted read-only.

Exported surface per engine module, namespaced per engine:

```
slipgate_init(argc_ptr, argv_ptr) -> i32
slipgate_step(elapsed_millis) -> i32     // returns frame status flags
slipgate_framebuffer() -> i32            // pointer into linear memory
slipgate_framebuffer_size() -> i32
slipgate_palette() -> i32                // pointer to 256 * 3 bytes
slipgate_audio_drain(dst_ptr, frames) -> i32
slipgate_push_event(type, code, value)
slipgate_save_state(dst_ptr, capacity) -> i32
```

Imported host functions: clock, log, file operations via WASI, and a fatal-error hook.

### 5.3 Emscripten flags and the Chasm constraints

Chasm targets Wasm 3.0 without Memory64 and without vector instructions, and supports the
Exception Handling proposal. That dictates:

- `-fwasm-exceptions` and `-sSUPPORT_LONGJMP=wasm`, because the engines use
  `setjmp`/`longjmp` for their error paths. This is verified against Chasm with a minimal
  reproduction in the very first engine change; if it does not hold, everything downstream is
  blocked and the blocker is raised rather than worked around.
- **Never** `-msimd128`. Any SIMD instruction makes the module unloadable on Android. CI
  greps the disassembled module for vector opcodes.
- No pthreads. Single-threaded builds only.
- `-Oz` or `-O3`, benchmarked. Interpreter performance is dominated by instruction count, so
  smaller is often faster.
- `-sINITIAL_MEMORY` set per engine. Hexen needs a larger zone than Doom; the number is
  measured and documented in the engine's build script.

One `.wasm` per engine, written into the corresponding game module's resources, with its
SHA-256 recorded in `SOURCES.lock`.

## 6. Graphics and shaders

Two backends in the original plan, because WebGPU coverage is not universal; a third is
added by [addendum 01 §B](02-addendum-01.md#b--a-third-graphics-backend).

- **Primary: WebGPU.** Browser WebGPU on web, Jetpack WebGPU (Dawn / Vulkan) on Android.
  Shared WGSL shaders in `commonMain` resources.
- **Fallback: classic.** Android `Bitmap`/`Canvas`, browser Canvas 2D. Feature-detected,
  with automatic fallback and the active backend surfaced in a debug overlay.

### 6.1 Game rendering

Palette-indexed upload: the 8-bit framebuffer goes up as an R8 texture and the 256-entry
palette as a small lookup texture, with colour resolved in the fragment shader. The palette
lookup never happens on the CPU — that is the single most important performance decision in
the renderer, and it makes palette effects free.

Post chain, each pass independently toggleable:

1. Integer-scale or FSR1 upscale.
2. CRT effect — barrel distortion, scanlines, aperture grille, slight bloom.
3. Optional colour grading per gate.

### 6.2 Menu shaders

The launcher must not look like a list view.

- **Attract background.** A palette-cycled fire simulation in a compute or fragment shader,
  seeded from the highlighted gate's own palette: red for Doom, green-gold for Heretic, cold
  blue for Hexen. The palette is read from the mounted game data, so the menu is themed by
  the game it is about to launch.
- **Gate cards.** Cover art with a subtle parallax tilt on selection, a chromatic aberration
  pulse when the selection changes, and a dissolve-in for cards whose data is not installed,
  rendered desaturated behind a noise mask.
- **Warp transition.** On launch: radial UV distortion pulling toward centre, scanline tear,
  palette collapse into the target game's first frame. This is the signature animation and
  gets real time budgeted for it.
- **Idle attract mode.** After roughly 30 seconds without input, a demo loop or animated
  marquee. Optional for v1, but the hook exists.

Every menu shader has a non-shader fallback that still looks intentional on the classic
backend: degraded, not broken.

Typography: one distinctive display face for gate titles, one legible UI face. No platform
defaults for the title treatment.

## 7. Platform parity

Every target is first-class. "Runs on a platform" means:

- Android — `./gradlew :android:assembleRelease` produces an installable APK; the app boots
  to the launcher, loads a gate, renders, accepts touch input, plays audio, saves and
  restores.
- Web — `./gradlew :web:wasmJsBrowserDistribution` produces a distribution meeting the same
  criteria with keyboard input and pointer lock.
- CI runs every platform build plus a headless smoke test on every pull request.
- Any `expect`/`actual` pair has all its actuals implemented in the pull request that
  introduces the `expect`.

The web distribution deploys to GitHub Pages from `main`.

## 8. Game data and licensing

- Chocolate Doom and the Raven engines are GPLv2, so derived `.wasm` modules are GPLv2. The
  host is dual MIT / Apache 2.0. The boundary is documented in the README and in
  `LICENSE-NOTES.md`, which states plainly that the combined-work question is contested
  rather than settled.
- No IWADs in the repository, in releases, or in CI caches. Not shareware, not freeware, not
  for testing.
- Data acquisition is a first-run flow: Freedoom for Doom, Blasphemer for Heretic, both
  offered as downloads from their official release pages or replaced by a user-supplied
  IWAD; Hexen is user-supplied only and its gate card says so instead of showing a dead
  download button.
- Supplied files are validated by IWAD header and lump signature, not by filename, with a
  clear error for an unrecognised or corrupt file.
- Game data lives in platform-appropriate app storage — app-private on Android, OPFS or
  IndexedDB on web — and never in the artifact.

## 9. Branches, pull requests and labels

Branch naming, pull request requirements and the label set are restated in
[CONTRIBUTING.md](../../CONTRIBUTING.md). The labels created for the project are eight
`type:` labels, the `area:` labels covering host-runtime, graphics, shaders, launcher,
controls, audio, engine-build and game-data, `platform:` labels per target, `gate:` labels
per game, plus `blocked` and `needs decision`.

Label application may be automated by path where it is unambiguous, but the `type:` label is
always set deliberately.

## 10. CI gates

All required on `main`:

1. **Static analysis** — ktlint and detekt, zero warnings tolerated.
2. **Build Android** — `:android:assembleRelease`, unsigned.
3. **Build Web** — `:web:wasmJsBrowserDistribution`.
4. **Unit tests** — `allTests` across common and platform source sets.
5. **Wasm integrity** — every shipped `.wasm` matches the SHA-256 in `SOURCES.lock`; fails if
   a module was rebuilt without updating the lock.
6. **No-SIMD check** — disassemble each module and fail on any vector opcode.
7. **Determinism smoke test** — instantiate each gate headless under Chasm on the JVM, play
   back a recorded demo lump for N tics, and compare a hash of the final game state. Engine
   demo playback desyncs loudly when a port is subtly wrong, which makes this the
   highest-value test in the project. It is built early.
8. **Commit lint** — Conventional Commits on every commit in the pull request, and no
   attribution strings in any commit message, pull request body or source comment.
9. **Pull request size advisory** — warn above 400 changed lines, excluding generated code.

## 11. Delivery plan

One pull request per item, in order, each with its acceptance criterion. Amendments and
inserted items are in [addendum 01 §F](02-addendum-01.md#f--plan-deltas).

**Phase 1 — Foundations**

1. `build`: Gradle setup, version catalog, KMP targets, detekt and ktlint, Koin wiring.
   *Green build on every target with a placeholder screen.*
2. `ci`: every gate from §10 that does not need a wasm module yet. *CI passes.*
3. `docs`: README with the mood acknowledgement, licensing notes, contributing guide.
4. `feat(host-runtime)`: the gate contract, with a fake in-memory gate rendering a test
   pattern. *Test pattern visible on every platform.*

**Phase 2 — Pixels**

5. `feat(graphics-core)`: backend contract, viewport maths, backend selection and fallback.
6. `feat(graphics-classic)`: Canvas 2D and Android Bitmap backends.
7. `feat(graphics-webgpu)`: palette-indexed WGSL pipeline, both drivers.
8. `feat(graphics-crt)`: CRT effect pass with toggles.

**Phase 3 — First gate**

9. `build(tooling)`: Chocolate Doom fetch script, `SOURCES.lock`, reproducible build entry
   point. *Two consecutive runs produce a byte-identical module.*
10. `build(tooling)`: wasm platform layer and loop inversion for Doom, including the
    longjmp and exception-handling verification. *Module loads under Chasm on the JVM.*
11. `feat(games-mars)`: Doom gate against the contract.
12. `feat(controls)`: virtual gamepad and keyboard mapping via `InputProfile`.
13. `feat(audio)`: PCM sink and ring-buffer drain.
14. `feat(game-data)`: IWAD picker, validation, Freedoom download flow, storage.
15. `test`: demo playback determinism harness. *Doom demo runs to completion in sync.*

**Phase 4 — The face**

16. `feat(launcher)`: gate registry, select screen layout, navigation, state model.
17. `feat(shaders)`: palette-derived attract background.
18. `feat(shaders)`: gate card effects and selection feedback.
19. `feat(shaders)`: slipgate warp transition.
20. `feat(launcher)`: in-game overlay menu, settings, return to launcher.

**Phase 5 — More gates**

21. `build(tooling)`: Heretic platform layer.
22. `feat(games-corvus)`: Heretic gate, including inventory input extensions.
23. `build(tooling)`: Hexen platform layer, larger zone allocation.
24. `feat(games-korax)`: Hexen gate, including multi-file hub saves.
25. `feat(game-data)`: Blasphemer flow, and Hexen user-supplied-only messaging.

**Phase 6 — Polish**

26. `perf`: interpreter profiling; document the frame budget per gate per platform.
27. `feat(graphics-fsr1)`: FSR1 upscaler.
28. `feat`: save-state suspend and resume across app backgrounding.
29. `ci`: GitHub Pages deployment.
30. `docs`: final README with architecture diagram, screenshots and build instructions.

## 12. Working method

- Before each phase, restate its plan and flag anything that has become wrong since this
  document was written. A stale instruction is never followed silently.
- Verify every dependency version and every upstream tag against the real source.
- When something is ambiguous, choose the option that keeps `host/*` game-agnostic and
  record the decision in the pull request body.
- When something is blocked, apply the `blocked` label and say so plainly rather than
  building a workaround that will need unpicking.
- Prefer deleting code over adding a configuration flag for it.

## 13. Definition of done for v1

- Three gates playable end to end on every target, from user-supplied or freely licensed
  data.
- Launcher with working shader effects on the WebGPU path and a coherent fallback.
- Demo determinism tests green for all three engines.
- No commercial game data anywhere in the repository or its history.
- README credits mood and Chasm clearly.
- Full Conventional Commits history, micro committed, with no attribution text anywhere in
  the repository.
