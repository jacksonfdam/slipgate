# Addendum 01

Amends [01-foundation.md](01-foundation.md). Everything not mentioned here stands unchanged,
including every non-negotiable rule: English only, micro commits, Conventional Commits, one
pull request per feature with labels, rebase and merge, Koin, dark mode, verified latest
stable versions, no game data in the repository.

## Priorities

Two changes were time-sensitive because their cost grows with every merged pull request:

1. **The iOS targets land immediately**, before any further `expect` declaration. Every
   `expect` merged without an iOS `actual` is debt that compounds.
2. **The gate contract gains the backend axis** (§C) before a second gate exists. If the
   contract has already merged, this is a `refactor`, not a rewrite — the shape barely
   changes.

## A — iOS becomes a first-class target

Amends §3 and §7.

Target platforms are Android, Web (wasmJs) and iOS. The project thesis is one Kotlin
Multiplatform codebase driving real game engines across three platforms. A feature working
on two of three is not done.

Module layout gains:

```
├── ios/                          (Xcode project + KMP framework)
└── host/graphics/backend/skia/   (iOS rendering backend)
```

### Requirements

- Targets `iosArm64` and `iosSimulatorArm64`. Compose Multiplatform for `launcher` and `ui`.
- Audio via `AVAudioEngine` through cinterop.
- `host/controls` is shared between Android and iOS — the virtual gamepad is written once.
- **Game data import:** `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` in
  `Info.plist`, so the app's Documents folder appears in the Files app. That is how a user
  drops in an IWAD, mirroring the folder workflow of the Android ports. `UIDocumentPicker`
  is also wired for explicit import.
- Signing: free-provisioning sideload or a personal developer account. CI builds unsigned for
  the simulator only.
- Every `expect` has all three actuals in the pull request that introduces it.

### Distribution position

**Slipgate will not be submitted to the App Store.** Stated in full in the README; the short
version is that GPLv2 has never been cleanly compatible with App Store distribution, and a
post-launch takedown against engines carrying decades of contributors is worse than never
submitting. iOS distribution is build-from-source, sideload, TestFlight, or an alternative EU
marketplace under the DMA.

## B — A third graphics backend

Amends §6. There is no WebGPU on iOS.

| Backend | Platforms | Shader language |
|---|---|---|
| WebGPU | Web (browser API), Android (Jetpack WebGPU / Dawn / Vulkan) | WGSL |
| Skia `RuntimeEffect` | iOS (Skiko, Metal underneath) | SkSL |
| Classic | Android `Bitmap`/`Canvas`, browser Canvas 2D | none |

Skia is chosen over raw Metal because Compose already ships Skiko, and the effects needed
here — palette lookup, scanlines, barrel distortion, the menu shaders — do not require
Metal-level control. Revisit only if profiling says otherwise.

### Shader authoring discipline

Two dialects means duplication, and duplication means drift:

- Every shader is a `ShaderProgram` with a name, a documented uniform block, and per-dialect
  sources in sibling resource files with identical structure and comments.
- Shader strings are **never** hand-written inline in Kotlin. Resource files, loaded by name.
- A cross-backend golden image test is mandatory. It is what stops the iOS path from quietly
  diverging from the WebGPU path.

If WGSL shaders were written inline first, the SkSL change is the moment to extract them to
resources, as its own `refactor` commit.

## C — The backend axis

Amends §4.

`BackendId` is `Wasm` or `Native`. Gates expose factories per backend; a `BackendResolver`
picks per platform, honouring a user override in settings.

```kotlin
interface Gate {
    val descriptor: GateDescriptor
    fun requirements(): DataRequirements
    fun sessionFactories(): Map<BackendId, GateSessionFactory>
}

fun interface GateSessionFactory {
    suspend fun create(data: MountedGameData, host: GateHost): GateSession
}
```

`GateSession`, `GateHost`, `InputFrame` and `InputProfile` are unchanged.

Module layout gains `host/backend/wasm/` and `host/backend/native/`. New hard rule:
**`host/runtime` must not depend on `host/backend/*`** — the runtime defines contracts,
backends implement them, the platform entry point wires them.

| Gate | Web | Android | iOS |
|---|---|---|---|
| mars, corvus, korax | Wasm (browser engine) | Wasm (Chasm) | Wasm (Chasm) |
| chthon (Quake) | Wasm (browser engine) | Native | Native |

Chasm has no JIT and no vector instructions, which is fine for id Tech 1 — mood demonstrates
a steady 35 fps at 320×200 — but not for Quake's software renderer, which was hand-tuned
assembly targeting a Pentium. QuakeDroid demonstrates the native software-renderer path
working on mobile.

Only the `Wasm` backend is implemented for now. The axis exists so that phase 7 is an
addition rather than a redesign; an unused enum value costs nothing.

## D — Additional labels

`platform: ios`, `backend: wasm`, `backend: native`, `gate: chthon`.

Pull request bodies report platform coverage for three platforms, each marked done or given
an explicit reason.

## E — Additional CI gates

- **Build iOS** — macOS runner: link the KMP framework, then `xcodebuild` for the simulator.
  Unsigned.
- **Cross-backend golden image** — feed a fixed indexed framebuffer plus a fixed palette
  through every available backend and compare against a reference image within tolerance.

## F — Plan deltas

Inserted items use letter suffixes so existing numbers stay valid. An item whose work has
already merged is skipped; an item amending merged work lands as a `refactor` or `feat`
against the existing code rather than reopening the original.

**Phase 1**

- `1a` `build`: add `iosArm64` and `iosSimulatorArm64` targets. *Green build on all three
  platforms.*
- `1b` `build`: iOS Xcode project and framework integration. *App launches in the simulator.*
- `2a` `ci`: macOS job building the iOS framework and simulator app.
- `4a` `refactor(host-runtime)`: introduce `BackendId`, `GateSessionFactory` and
  `BackendResolver` per §C. *Existing fake gate still renders on all three platforms.*

**Phase 2**

- `7a` `feat(graphics-skia)`: SkSL palette-indexed pipeline for iOS.
- `7b` `test(graphics)`: cross-backend golden image harness. *All three backends agree.*
- Item 8 (CRT) covers both shader dialects.

**Phase 3**

- Item 12 (controls) covers iOS touch input.
- Item 13 (audio) covers `AVAudioEngine`.
- Item 14 (game data) covers the iOS Files-app integration from §A.
- If any of 12–14 has already merged, `12a` / `13a` / `14a` bring iOS to parity, before
  phase 4 starts.

**Phase 6**

- Item 27 (performance documentation) becomes a per-platform, per-gate fps table in the
  README. That number is part of the story, not an internal note.
- Item 30 (final README) requires captured GIFs of all three platforms running, plus the warp
  transition.

**Phase 7 — Native backend and Quake** *(new; confirm before starting)*

Decision gate: the NDK matrix, per-ABI APK growth and a second build pipeline are real
costs. The Quake gate is confirmed as still wanted before paying them. If it is not, this
phase is deleted — nothing else depends on it.

- `31` `build(tooling)`: native platform layer, NDK and CMake for Android, in
  `tooling/engine-build-native/`.
- `32` `feat(backend-native)`: JNI bridge, Doom gate as the proof. The framebuffer **must**
  cross the boundary zero-copy via a `DirectByteBuffer` wrapping the engine's framebuffer; a
  per-frame copy erases the reason for going native.
- `33` `build(tooling)`: Xcode static library and Kotlin/Native `cinterop` definition for
  iOS. No JNI needed — Kotlin/Native calls the C directly, which is materially simpler than
  the Android path.
- `34` `feat(backend-native)`: native session on iOS.
- `35` `build(tooling)`: Mark V **WinQuake** platform layer, matching QuakeDroid's
  `Android_WinQuake` tree. Never GLQuake — it renders to its own surface and bypasses the
  entire graphics stack. WinQuake outputs an 8-bit palette-indexed framebuffer, so it feeds
  the same pipeline as every other gate.
- `36` `feat(games-chthon)`: Quake gate — native on Android and iOS, wasm on web. Data is
  user-supplied `pak0.pak` and `pak1.pak` from `id1` only; never bundled.
- `37` `feat(launcher)`: backend override in settings, exposing `Native` as an opt-in
  performance mode for the Doom-family gates too.

The native platform layer's exported surface stays **identical** to the wasm one
(`slipgate_init`, `slipgate_step`, `slipgate_framebuffer`, …) so `host/runtime` sees one
shape regardless of backend.

## G — Attribution additions

The README acknowledgement of mood and Chasm stands. Where used, credit **Mark V** and
**QuakeDroid** — specifically the `Android_WinQuake` build that makes a native Quake gate fit
the palette pipeline.

## H — Retrofit rules

- An `expect` that merged without an iOS `actual` is a `fix`, not a `feat`, and it blocks the
  next feature pull request in that module.
- Existing plan items are not renumbered and merged history is not rewritten.
- Where an instruction here conflicts with merged work, the pull request says so explicitly
  and proposes the smaller change. The stale version is not followed silently, and working
  code is not rewritten to match a document.
