# Engine build

Slipgate runs real engines, and this directory is where their source comes from and where the
WebAssembly modules get built. Nothing here is vendored: `.sources/` is gitignored, and the
repository holds a pin rather than a copy of anyone else's tree.

## Fetching

```
./fetch-sources.sh            # clone at the recorded commit and verify it
./fetch-sources.sh --update   # move the pin to CHOCOLATE_DOOM_REF and rewrite SOURCES.lock
```

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

`D_RunFrame` already exists in this revision (`src/doom/d_main.c`), and `D_DoomLoop` is a loop
around it. The engine's main loop therefore does not need splitting by hand: the port calls the
initialisation path once and `D_RunFrame` per step, which is what makes a stepped session possible
at all.

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
