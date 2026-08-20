# Engine build

Slipgate runs real engines, and this directory is where their source comes from and where the
WebAssembly modules get built. Nothing here is vendored: `.sources/` is gitignored, and the
repository holds a pin rather than a copy of anyone else's tree.

## Fetching

```
./fetch-sources.sh            # clone at the recorded commit and verify it
./fetch-sources.sh --update   # move the pin to CHOCOLATE_DOOM_REF and rewrite SOURCES.lock
```

## Building

```
./build-doom.sh               # writes .build/mars.wasm
./build-heretic.sh            # writes .build/corvus.wasm
./build-hexen.sh              # writes .build/korax.wasm
```

`build-engine.sh` is the build; the per-game scripts are what differs between games — the directory
under `src/`, the module's name, how much memory its zone needs. A number guessed once and shared is
a number nobody owns, which is why each game states its own.

`SOURCES.lock` records the repository, the tag and the commit. The commit is what gets built; the
tag is there so a human can see which release it is. A tag that moves upstream fails the fetch
rather than silently changing what Slipgate builds.

## What is being ported

Chocolate Doom carries Doom, Heretic, Hexen and Strife in one tree behind a shared `i_*` platform
layer, which is why three of Slipgate's gates come from a single upstream source.

The port replaces that platform layer rather than patching the engines:

| Upstream file | Replaced by |
|---|---|
| `src/i_video.c` | a framebuffer in linear memory, with its address exported |
| `src/i_sound.c` | PCM written into a ring buffer the host drains |
| `src/i_input.c` | an event queue the host writes into |
| `src/i_timer.c`, `src/i_system.c` | imported host functions |

Each game's main loop is inverted the same way and in one file, `platform/<game>/sg_engine.c`: the
exported surface asks for a frame and never learns which game answers.

`D_RunFrame` already exists for Doom in this revision (`src/doom/d_main.c`), and `D_DoomLoop` is a
loop around it, so Doom's frame is one call. Heretic was never factored that way upstream: its
`D_DoomLoop` is still a `while` loop around `I_StartFrame`, `TryRunTics`, `S_UpdateSounds` and
`D_Display`, so its frame is those four calls made from the platform layer. Either way the port
calls the initialisation path once and a frame per step, which is what makes a stepped session
possible at all — and either way the upstream tree stays unpatched.

Hexen needs a third answer, because its loop body cannot be called at all: `DrawAndBlit` is static to
`h2_main.c`, along with the page drawer and the message drawer it calls. Copying it into the platform
layer would mean copying its file-local state, and a copy that drifts from the engine is worse than no
copy. So `platform/hexen/sg_engine.c` runs **the engine's own loop for exactly one iteration**:
`H2_GameLoop` is entered per step and left again at the top of its second pass, through
`sg_engine_frame_boundary` — the hook `I_StartFrame` calls at every frame boundary, which does nothing
for the two engines that have a frame function of their own. What runs in between is Hexen's real loop
body, statics and all.

Heretic needs one thing Doom does not: its `d_main.c` calls Chocolate Doom's textscreen library
directly to draw a DOS-style loading screen. That library is an SDL window with a bitmap font in it,
which has no place inside a gate, so `platform/include/txt_main.h` declares the calls that file
makes and `platform/heretic/sg_textscreen.c` implements them as nothing. `TXT_Init` reporting
failure is a path the engine already handles: it clears `using_graphical_startup` and boots without
the loading screen.

Hexen has the same shape of problem in a different place: it boots behind a 640x480 planar VGA screen
emulated in `i_videohr.c`. `I_SetVideoModeHR` reporting failure is what makes `st_start.c` skip it,
and the rest of that family are no-ops in `platform/sg_stubs.c` beside the CD player and the joystick.

## The toolchain

Emscripten is pinned to **6.0.7** and installed into `.toolchain/`, which is gitignored:

```
git clone --depth 1 https://github.com/emscripten-core/emsdk.git .toolchain/emsdk
cd .toolchain/emsdk && ./emsdk install 6.0.7 && ./emsdk activate 6.0.7
```

On macOS the SDK needs `EMSDK_PYTHON` pointed at Python 3.10 or newer, because the Python on the
default path comes from the Xcode tools and is 3.9. The build scripts set it themselves.

### The flag that is not optional

```
-fwasm-exceptions -sSUPPORT_LONGJMP=wasm -sWASM_LEGACY_EXCEPTIONS=0
```

The engines use `setjmp`/`longjmp` for their error paths, and Emscripten implements wasm longjmp on
top of exception handling. Emscripten still emits the **legacy** exception opcodes by default, and
Chasm implements the final proposal, so a module built without `-sWASM_LEGACY_EXCEPTIONS=0` fails to
decode with `UnknownInstruction(byte=6)` — that is the legacy `try` — before a single instruction
runs. `host/backend/wasm` carries the probe that proves the working combination.

Never pass `-msimd128`. Chasm has no vector instruction support, and
`tooling/ci/verify-wasm-artifacts.sh` fails any module containing the SIMD prefix.

## Licensing

The engines are GPLv2 and so is everything built from them, including the platform layer here,
which is written against the engines' internal interfaces. See `LICENSE-NOTES.md` at the
repository root. No game data belongs anywhere near this directory.
