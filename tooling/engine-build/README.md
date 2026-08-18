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

## Licensing

The engines are GPLv2 and so is everything built from them, including the platform layer here,
which is written against the engines' internal interfaces. See `LICENSE-NOTES.md` at the
repository root. No game data belongs anywhere near this directory.
