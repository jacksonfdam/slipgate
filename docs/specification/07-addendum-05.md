# Addendum 05 — the Hexen II gate

Amends [02-addendum-01.md](02-addendum-01.md) §F with a phase 8, and §G with one more
attribution. Amends [06-addendum-04.md](06-addendum-04.md) in one place only: the Quake-family
platform layer it introduces becomes family-shaped, the way the Doom-family one already is.
Everything else stands, including every non-negotiable rule.

Nothing here is built yet. It is written before the work so the decisions are arguable while
they are still cheap.

## Why Hexen II is the next gate after Quake

Hexen II is Raven's Quake-engine game, the way Heretic and Hexen were Raven's Doom-engine games.
That single sentence is the whole argument: the reason a Heretic gate and a Hexen gate cost a
platform layer each rather than a port each is that Chocolate Doom carries all three behind one
`i_*` seam. Phase 7 builds the same kind of seam for a Quake engine. Hexen II is what is on the
other side of it.

The port is [uHexen2](https://github.com/sezero/uhexen2) — *Hexen II: Hammer of Thyrion*,
maintained by O. Sezer, GPLv2, a real git repository with release tags. Three things about it
matter here:

- **Its software renderer is 8-bit and stays 8-bit.** `engine/h2shared/vid_sdl.c` says so at the
  top of the file: bpp is 8 and "fairly hardwired at this depth". That is the whole reason this
  gate is possible — a palette-indexed framebuffer is what the host's pipeline eats, and the GL
  build (`glhexen2`) would bypass every part of it. The gate builds `hexen2`, never `glhexen2`.
- **The x86 assembly is optional.** The makefiles carry `USE_X86_ASM=no`, which is the same
  switch as Doom's `id386=0`: C paths only, on every ABI we ship.
- **It needs no mirror.** Mark V had no upstream repository, which is why item 35a exists.
  uHexen2 has tags, so `SOURCES.lock` pins it exactly the way it pins Chocolate Doom, and the
  GPL's source-availability obligation is satisfied by the upstream itself.

The engine tree splits as `engine/h2shared` (renderer, video, sound, sys, filesystem) and
`engine/hexen2` (client, server, game logic). `engine/hexenworld` — the multiplayer fork, its
own client, server and master server — is out of scope and stays that way; this host has no
networking and no reason to grow any.

### What was checked and not used

**QuakeSpasm was read and rejected as an engine, for Quake and for anything else here.** It is
OpenGL only: its tree is `gl_draw.c`, `gl_rmain.c`, `gl_texmgr.c`, `gl_vidsdl.c` with no
software rasteriser anywhere in it. There is no 8-bit indexed framebuffer to hand the palette
pipeline, so it cannot feed this host without replacing the graphics stack it would be feeding.
Its `Makefile.emscripten` is genuinely interesting and genuinely irrelevant for the same reason.

**The Quake gate's engine choice is not reopened.** Mark V WinQuake stands, confirmed
2026-08-19 and recorded in addendum 04. uHexen2 runs Hexen II progs and Hexen II protocol; it is
not a Quake engine wearing a different name, and nothing about reading it argues for a different
`chthon`.

What reading uHexen2 *did* settle is cheaper and more useful: it confirms the shape of the seam
phase 7 is about to build, in a second, independently maintained descendant of the same
codebase. `Host_Init` plus `Host_Frame`, `gfx/palette.lmp` out of a pak, `config.cfg` written on
the way out, stdio underneath the pak reader, `cd_null.c` already in the tree — every one of
those is a decision addendum 04 already made for `chthon`, and Hexen II makes the same one.

## The name

`eidolon`, after the last and most powerful of the Serpent Riders, who is what the four heroes
of Hexen II are walking towards. It continues the arc the rack already has: `korax` took the
world after his brother fell, and `eidolon` is the one who sent them both. Two syllables, hard,
mythic, and named for the antagonist exactly as `korax` and `chthon` are.

`praevus` — the Archbishop of the *Portal of Praevus* mission pack — was the alternative, and it
names the expansion rather than the game. The mission pack is in scope as an add-on (item 43),
which is a further reason not to spend the name on it.

The id reaches storage keys, the `gate: eidolon` label and any deep link, so it is settled here
rather than at the first commit.

## What Hexen II needs that no gate before it did

- **A 320×240 frame.** `MIN_WIDTH` is 320 and the default mode is 320×240, not the 320×200 every
  existing gate produces. The host already claims nothing assumes those dimensions
  ([README](../../README.md)); this is the gate that proves the claim, and any place it turns out
  to be false is a `fix` against the runtime rather than a compromise in the gate.
- **A frame the engine may refuse.** `Host_FilterTime` drops any frame less than 1/72 s after
  the last one, and `Host_Frame` splits a longer interval into sub-steps of at most 1/72 s. So
  one host step at 28.6 ms is two engine sub-frames, and a step that arrives too early renders
  nothing at all. The platform layer feeds the clock, so it decides this; what it must not do is
  return a frame the engine never drew and call it rendered.
- **Data that is strict twice over.** Both `pak0.pak` and `pak1.pak`, and both patched to
  version 1.11. See below.
- **98 MB of paks.** `pak0` is 22.7 MB and `pak1` is 75.6 MB, against Freedoom's 38 MB and
  Hexen's 20 MB. Mounted whole into module memory, on top of a 32 MiB hunk, that is a footprint
  no existing gate approaches — which is why item 45 exists and why it is written as conditional
  on a measurement rather than as a promise.
- **Saves that are directories.** `Host_Savegame_f` calls `Sys_mkdir`, writes `info.dat` into
  the new directory, and drops one `.gip` file per visited map beside it, plus `clients.gip`.
  Hexen already spread a hub across files; this spreads a *slot* across a directory, and the
  save seam in `platform/sg_files.c` has neither directories nor room for six slots' worth of
  them at `MAX_FILES 64`. Item 41.
- **A look axis and a crouch.** `+lookup`, `+lookdown`, `centerview` and `+crouch` are all real
  binds here. `GateAction.Crouch` already exists and has never been used by a gate; the look
  axis is what item 37a is for, which is why this gate depends on it rather than shipping with
  an unbound half of its input surface.
- **An inventory of fourteen items, and no key for most of them.** `invleft`, `invright`,
  `invuse`, `invdrop` and `+showinfo` walk it; `impulse 100`–`114` jump straight to a named
  item. The gate draws the walk, the way `korax` does, and does not draw a keyboard.
- **No music at all, honestly.** Hexen II's music is MIDI inside the paks, played through
  timidity — which needs a GUS patch set the player does not have — or ripped CD tracks the
  player would have to supply as `.ogg`. Neither is game data this project can be involved in.
  `USE_MIDI=no`, every codec off, `bgmnull.c` in the link, and the gate says nothing about music
  it cannot play. Sound effects are WAV inside the paks and work normally.
- **No demo to replay.** The Doom-family harness proves determinism with `-playdemo`, and
  addendum 04 gives `chthon` `playdemo demo1` for the same reason. Hexen II ships no `.dem`
  files: its `demo1` is a *map* — Blackmarsh — and `startdemos` has nothing to list. The harness
  is therefore two runs of the same map from a fixed clock with no input, compared frame for
  frame. That is a real proof here because the engine never calls `srand`, so its `rand()`
  sequence is the same on every run.
- **Four character classes, chosen in the engine's own menu.** Paladin, Crusader, Necromancer,
  Assassin, and the Demoness with the mission pack. No host surface: the menu is the engine's.

## Game data

Strict, and for a second reason beyond the one `chthon` has: uHexen2 requires the retail data to
be at version 1.11, the state Raven's own patch leaves it in. It identifies what it was given by
counting the files in the pak directory and CRC-ing it — `engine/h2shared/quakefs.c` carries the
whole table — so a rejection here can name the exact version the player supplied.

| Data | Files | CRC | Bytes | Verdict |
|---|---|---|---|---|
| `data1/pak0.pak`, registered v1.11 | 696 | 34289 | 22,704,056 | Required |
| `data1/pak1.pak`, registered v1.11 | 523 | 2995 | 75,601,170 | Required |
| `portals/pak3.pak`, Portal of Praevus | 245 | 1478 | 49,089,114 | Optional add-on |
| `data1/pak0.pak`, demo v1.11 | 797 | 22780 | 27,750,257 | Named and refused |
| `data1/pak0.pak`, OEM v1.10 *(Continent of Blackmarsh)* | 697 | 9787 | 22,720,659 | Named and refused |
| `data1/pak2.pak`, OEM v1.10 | 183 | 4807 | 17,742,721 | Named and refused |
| `data1/pak0.pak`, retail v1.03 | 697 | 53062 | 21,714,275 | Named and refused |
| `data1/pak1.pak`, retail v1.03 | 525 | 47762 | 76,958,474 | Named and refused |
| `hw/pak4.pak`, HexenWorld | 102 | 41062 | 10,780,245 | Named and refused |

The md5 sums upstream publishes for the three accepted files are
`c9675191e75dd25a3b9ed81ee7e05eff`, `c2ac5b0640773eed9ebe1cda2eca2ad0` and
`77ae298dd0dcd16ab12f4a68067ff2c3`.

Three refusals are worth wording carefully, because each one is a player who owns the game and
is being told no:

- **Pre-1.11 retail.** "That is Hexen II 1.03 from the original discs; this gate needs the 1.11
  data Raven's patch produces." Upstream can be compiled to accept it — `ENABLE_OLD_RETAIL` —
  and deliberately is not, here or there: upstream prints "old/unsupported" for that path, and a
  gate that boots into subtly wrong gamecode is worse than a gate that explains itself. The
  patcher, `h2patch`, is not something this project can run for the player: it applies xdelta
  deltas that are themselves derived from the game data, and nothing derived from game data
  enters this repository or gets downloaded by it.
- **The demo.** Recognised, named, refused. uHexen2 redistributes the demo data itself, so the
  temptation is real, and it is still not a freely *licensed* replacement in the sense Freedoom
  and Blasphemer are. The precedent that matters is the shareware Doom WAD, which this project
  has never offered either.
- **Mixed versions.** `pak0` from one release beside `pak1` from another is the one case upstream
  treats as fatal — `Sys_Error ("Bad Hexen II installation: mixed data from incompatible
  versions")`. The host catches that combination before boot and says which of the two files is
  the odd one, rather than letting the engine die inside a gate.

Nothing is ever downloaded or bundled. The gate card says user-supplied only, the way `korax`'s
already does.

The engine also verifies retail data a second way — `CheckRegistered` looks for a graphic that
only the registered paks contain, and calls `Sys_Error` when it is missing. That is a seam
correctness test, not a data policy: if the in-memory pak mount is right, the check passes.

### Portal of Praevus

The mission pack is an add-on, but not the kind the shelf already knows. Custom maps arrive as
`-file` arguments over a game already installed
([04-addendum-03.md](04-addendum-03.md)); `pak3.pak` instead lives in a *second game directory*,
`portals/`, which the engine mounts when given `-portals` (or `-h2mp`). So the shelf gains a
kind of entry rather than another row of the same kind: recognised by content like everything
else, stored under its own key, and turned into a launch flag rather than a file list. It also
brings `+infoplaque` — the objectives screen — into the input profile, and a fifth class.

Item 43, after the base gate boots.

## What is inherited from phase 7 and not built again

This is the part worth being explicit about, because the two phases would otherwise overlap
badly.

| Phase 7 delivers | Phase 8 uses it as-is |
|---|---|
| 31 — dual-target platform layer, `SG_EXPORT`/`sg_ptr`, `replaced-sources.txt` | The whole native build shape |
| 32 — dlopen/JNI bridge, zero-copy framebuffer | Unchanged; a second native library is a second `dlopen` |
| 33, 34 — iOS static libraries, prefix header, cinterop, native session | The prefix mechanism is what keeps two Quake-family engines from colliding at link time |
| 35b — WinQuake platform layer | Split into shared and per-game, then reused. Item 39a |
| 35c — PAK inspection | Extended with a Hexen II flavour, not rewritten. Item 40 |
| 36 — the `chthon` gate | The module template, the harness shape, the credits pattern |
| 37 — backend override in Settings | Applies to a new gate for free |
| 37a — touch look | The reason this gate can have a look axis at all |

The one amendment to addendum 04: `platform-quake/` was described as a single directory of
`sgq_*.c`. It becomes shared files plus one directory per game — `platform-quake/quake/`,
`platform-quake/hexen2/` — which is exactly the shape `platform/` already has for `doom`,
`heretic`, `hexen` and `strife`. Item 39a does that as a refactor with `chthon`'s committed
artifacts rebuilding byte-identically, which is the proof that nothing moved but file names.

Whether item 39a lands as part of 35b or after it is the implementer's call: if 35b has not
merged when this phase starts, the family shape goes in there and 39a disappears. §H says to
propose the smaller change.

## Art

Two files, the same two every painted gate ships, as WebP under
`ui/src/commonMain/composeResources/files/backdrops/`:

| File | Size | Role |
|---|---|---|
| `cover_eidolon.webp` | 1024 × 1024 | The gate card in the rack |
| `bg_eidolon.webp` | 1920 × 1920 | The full-screen backdrop while the card is focused |

Then `eidolon` joins the `painted` set in `Backdrops.kt`. House style is the one the other five
share: pixel art, chunky visible dithering, hard-edged clusters, no airbrush gradients, and a
tight palette in one dominant hue drawn from the game's own — Hexen II's is cold stone and
green-black swamp against gold, which reads distinctly beside `korax`'s violet and bone.

The launcher's accent is sampled from the player's own `gfx/palette.lmp` once data is installed,
so the art only has to agree with it, not define it.

## Delivery plan

Inserted items continue the numbering, per §F. One pull request each, micro commits inside.

| Item | Scope | Depends on |
|---|---|---|
| 38 | `build(tooling)`: pin uHexen2 in `SOURCES.lock` and `fetch-sources.sh` | — |
| 39a | `refactor(tooling)`: `platform-quake` becomes family-shaped; `chthon` rebuilds byte-identically | 35b |
| 39b | `build(tooling)`: Hexen II platform layer and `eidolon.wasm` | 38, 39a |
| 39c | `build(tooling)`: native `libeidolon` for Android, iOS and the build host | 39b, 33 |
| 40 | `feat(host-gamedata)`: Hexen II pak recognition, `data1`/`portals` layout, palette accent | 35c |
| 41 | `feat(engine-build)`: the save seam grows directories and room for Hexen II's slots | 39b |
| 42 | `feat(games-eidolon)`: the gate on all three platforms, determinism harness, credits | 36, 37a, 39c, 40, 41 |
| 43 | `feat(launcher)`: Portal of Praevus as a mission pack on the shelf | 42 |
| 44 | `feat(design-system)`: `eidolon` cover and backdrop | — |
| 45 | `perf(engine-build)`: paks read on demand rather than mounted whole — conditional | 42 |

The critical path is 38 → 39a → 39b → 39c → 42. Items 40, 41 and 44 run parallel to it; 43 and
45 are the tail, and 45 only exists if 42's measurements say it does.

Specifics the implementing pull requests hold to:

- The build is `hexen2`, the software client. Never `glhexen2`, never HexenWorld, never the
  dedicated server. `USE_X86_ASM=no`, `USE_MIDI=no`, every audio codec off, `cd_null.c`,
  `bgmnull.c`, no SDL.
- The frame is 320×240, 8-bit indexed, and the palette generation counter reports
  `gfx/palette.lmp` changes through `SG_PALETTE_CHANGED` — which Hexen II exercises hard, since
  it tints for damage, for the Tome of Power's descendants and for water.
- Paks mount into an in-memory `data1/` tree — and `portals/` when the mission pack is
  installed — behind the same stdio seam `chthon` uses. `config.cfg` writes land in scratch
  memory: `Host_Shutdown` writes it on the way out and a gate must not crash while leaving.
- The determinism harness is two runs of one map from a fixed clock, hashing the framebuffer
  each step, gated on a `-Pslipgate.pak` property exactly as the Doom gate's harness is gated on
  its IWAD. It skips without real data, and — this is the lesson #50 cost — the gate is not
  believed to work until it has been run against real data on a device.
- `MINIMUM_MEMORY` is 0x550000 and `STD_MEM_ALLOC` is 32 MiB; the wasm module's initial memory is
  sized from a measurement, not from Hexen's 96 MiB copied across.

## Attribution

§G gains **uHexen2 / Hexen II: Hammer of Thyrion** and its maintainers, beside Raven Software's
and id Software's own credit for the engine underneath it. The credits screen entry lands with
the gate itself, in item 42.

## Open questions

- **Does 98 MB of mounted pak fit a phone?** Unmeasured, and the answer decides item 45. The
  measurement is worth taking early — during 39b, not after 42 — because a demand-read seam
  changes the platform layer rather than sitting on top of it.
- **What does a Quake-family engine cost per step on a mid-range phone?** `korax` on the Chasm
  interpreter is 5.0 ms median and 156.1 ms worst on a desktop JVM, and the phone column is a
  factor of ten worse than the desktop one for every gate measured. Hexen II is a heavier engine
  than Hexen. The native backend is the answer this phase inherits, but the wasm path is still
  what the web gets, and the README's frame-budget table should say so honestly once measured.
- **Which map is the harness map?** It has to exist in `pak0`, be reachable with `+map`, and
  settle within a few hundred frames without input. Picked with real data in hand, in item 42.
