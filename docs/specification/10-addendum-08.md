# Addendum 08 — the Wolfenstein 3D gate

Amends [02-addendum-01.md](02-addendum-01.md) §F with a phase 9, and §G with two more
attributions. Amends [LICENSE-NOTES.md](../../LICENSE-NOTES.md) with a licence category the
project has not needed before, which is the largest single fact about this gate and is argued
below rather than buried. Everything else stands, including every non-negotiable rule.

Nothing here is built yet. It is written before the work so the decisions are arguable while
they are still cheap.

## Why Wolfenstein 3D at all

Wolfenstein 3D is the game before the games this launcher already runs — the 1992 raycaster
whose success paid for Doom. Technically it is the easiest engine this project will ever add:

- **The frame is 320×200, 8-bit indexed.** The exact shape the host's palette pipeline eats,
  smaller in memory than any gate shipped, with a renderer that never grew a GL fork to be
  tempted by.
- **The engine is tiny.** The whole game fits in a few hundred kilobytes of code and a few
  megabytes of data. After Hexen II's 98 MB of paks, `grosse` is the gate that costs the least
  of everything — memory, module size, frame time — on every backend including the Chasm
  interpreter, where the Doom-family gates already struggle on phones.
- **It is a third engine family, and that is the point of the exercise.** The Doom family
  arrives through Chocolate Doom's `i_*` seam; the Quake family through the `sys_*`/`vid_*`
  seam phase 7 built and phase 8 made family-shaped. Wolfenstein's seam is `id_*` — `id_vl`
  for video, `id_sd` for sound, `id_in` for input, `id_ca`/`id_pm` for file access. A third
  family proves the host's claim that nothing in it assumes one engine's shape — the same way
  Hexen II's 320×240 frame proved nothing assumed 320×200.

What it is not: urgent. Phases 7 and 8 are in flight and stay ahead of it. This addendum
exists because the data and the source landed on the shelf and the decisions were worth
writing down while the reading was fresh.

## The engine, and the licence that comes with it

The base is **Wolf4SDL**, Moritz "Ripper" Kroll's port of the 1995 id source release —
original game logic kept deliberately intact, SDL underneath, every retail data variant
supported. It is the Chocolate Doom of Wolfenstein in everything but name: conservative,
vanilla-faithful, and the lineage nearly every later port descends from. The compilation
units are C++ but the code is C-shaped — the same id code the 1995 release published, with
the DOS and ASM layers replaced.

**ECWolf was read and rejected**: it is the ZDoom of Wolfenstein — launcher, mod support,
its own scripting — which is more engine than a vanilla gate wants and far more surface than
a platform layer should carry. **The 1995 `wolfsrc` itself was rejected as a direct base**:
Borland C for 16-bit DOS, planar VGA and inline x86 throughout; porting it from scratch would
reinvent exactly the work Wolf4SDL already did and has had thirty years of eyes on.

Wolf4SDL has no canonical upstream git repository with tags — it is a 2000s-era project whose
releases were zip archives, surviving in scattered forks. That is the Mark V situation, and it
gets the Mark V answer (addendum 04): a public mirror — `github.com/jacksonfdam/wolf4sdl`, one
commit per upstream release, the original URL and sha256 recorded in the mirror — so
`SOURCES.lock` pins it with the same `repository`/`tag`/`commit` shape as every other engine.

### The licence

This is the fact that makes `grosse` different in kind, not just in family. **The Wolfenstein
3D source was never re-licensed under the GPL.** Doom, Heretic, Hexen, Strife, Quake and
Hexen II all eventually got GPL releases; Wolfenstein's 1995 release came under id's own
licence, which permits use and modification for non-commercial purposes by owners of the game
and was never superseded. Wolf4SDL inherits it, and so would every module built from it.

`LICENSE-NOTES.md` currently tells a two-part story: MIT/Apache-2.0 host, GPLv2 engines. This
gate adds a third category, and the addendum's position is that it is acceptable **only if
told honestly**:

- The `grosse` modules are derivatives of the 1995 id source release and carry its licence —
  non-commercial, and the player must own the game. They are not GPL and must not be
  described as GPL.
- Slipgate itself is non-commercial and ships no game data, so nothing about the project's
  practice changes — pinned sources, published build scripts, complete corresponding source —
  but the *obligation* behind the practice is courtesy here rather than the GPL's terms, and
  the notes say so.
- The iOS section of the README gets one honest sentence: the App Store analysis written for
  GPLv2 does not transfer — this licence fails the store on different grounds (non-commercial
  terms against a paid distribution channel), and the existing conclusion (no App Store
  submission) already covers it.
- **The word "Wolfenstein" is a trademark with an active owner.** The gate's card, credits
  and documentation use the same unaffiliated-project posture every other gate uses, and the
  repository continues to ship no data, no art and no name from the game itself.

If any of that ever proves unworkable, the gate is removed rather than the story bent. The
engines' history has one clean precedent — Apple pulled a GPLv2 GNU Go port in 2010 — and
this project's stated preference is never submitting over post-launch takedowns.

## The name

`grosse`, after Hans Grosse — the armoured wall of a man with a chaingun in each fist at the
bottom of episode 1, the first boss id ever shipped in a first-person game. It follows the
rack's pattern exactly: `chthon` is Quake's first boss, and `grosse` is Wolfenstein's. One
hard syllable in speech, two in spelling, and it reads as a place-guardian the way the other
names do.

`blazkowicz` — the protagonist — was considered and rejected: four syllables, unspellable at
a glance, and the rack already has its protagonist name in `corvus`. `totenkopf` and
`schabbs` name later bosses with less recognition. The id reaches storage keys, the
`gate: grosse` label and any deep link, so it is settled here.

## What Wolfenstein needs that no gate before it did

- **Data as a set of loose files, not a container.** Every gate so far mounts one archive
  kind — WADs or PAKs — and the inspector reads one file and knows what it holds. Registered
  Wolfenstein is **eight sibling files**: `VSWAP` (walls, sprites, digitised sounds),
  `GAMEMAPS` + `MAPHEAD` (levels), `VGAGRAPH` + `VGAHEAD` + `VGADICT` (2D art, Huffman
  compressed), `AUDIOT` + `AUDIOHED` (AdLib sound and music). The extension names the variant
  — `.WL1` shareware, `.WL3` and `.WL6` registered, `.SOD` Spear of Destiny — but contents
  decide, as always: `MAPHEAD` opens with the RLEW tag `0xABCD`, `GAMEMAPS` with TED5's own
  signature, `VSWAP` with a chunk directory whose counts separate the variants. A requirement
  that names eight files and validates each by reading it is new surface in
  `DataRequirements`, and it is the reason item 54 is its own pull request.
- **A palette compiled into the engine.** There is no `PLAYPAL`, no `gfx/palette.lmp` — the
  palette is a table in the source. The launcher cannot sample an accent from installed data
  the way every other gate does, so the descriptor declares the accent against the engine's
  own known palette, constant from the day the gate is written. The in-game palette *shifts*
  — the damage flash, the item flash — arrive through `SG_PALETTE_CHANGED` exactly as they do
  everywhere else.
- **A 70 Hz heart.** Wolfenstein tics at the VGA vertical blank — 70 per second, adaptive:
  the game loop measures elapsed tics and simulates that many. The host contract already
  treats tic rate as a session property; the platform layer feeds the clock and the engine
  decides how many tics it owes, which is the same arrangement Hexen II's
  `Host_FilterTime` negotiates at 72.
- **Resources that live in the executable.** The original kept the signon screen, the PC-13
  error text and the attract demos inside the EXE rather than the data files. Wolf4SDL
  carries them in source, which is exactly where a wasm module wants them; the consequence
  worth writing down is that the determinism harness cannot assume demos exist as data (see
  below).
- **The simplest input profile the rack will ever have.** Move, strafe, turn, fire, use, run,
  four weapon slots, menu. No jump, no crouch, no look axis, no inventory, no automap. After
  Strife's eleven extensions and Hexen II's fourteen-item inventory, `grosse` is the gate
  where the pad finally has room to spare.
- **Music through an OPL2 emulator.** Wolfenstein's music is IMF — raw OPL register writes —
  and Wolf4SDL bundles a software OPL2 emulator in C to play it. No patch sets, no CD audio,
  no codecs: if the emulator compiles into the module and holds the frame budget, the gate
  has music for free; if it does not, the eidolon precedent applies — effects only, said
  honestly. Digitised effects come out of `VSWAP` and AdLib effects out of `AUDIOT` either
  way. Measured in item 55, decided before item 57.

## Game data

Strict, in the pattern `chthon` and `eidolon` set. **Registered Wolfenstein 3D — the
six-episode `.WL6` set — is required.** Nothing is downloaded, nothing is bundled; the card
says user-supplied only.

| Data | Reading | Verdict |
|---|---|---|
| `.WL6` set, eight files, v1.4 | Registered, six episodes, 60 maps | Required |
| `.WL3` set | Registered, three episodes, 30 maps | Recognised and named; whether it boots is decided in item 54 with the engine's own variant rules in hand, not guessed here |
| `.WL1` set | Shareware, episode 1, 10 maps | Named and refused |
| `.SOD`/`.SD1`–`.SD3` sets | Spear of Destiny and its mission sets, 21 maps each | Named and refused; item 60's business |
| Older revisions (v1.0–v1.2) | Recognised by their counts | Named with their version and refused, the eidolon pattern: the engine targets 1.4 behaviour and a gate that boots subtly wrong gamecode is worse than one that explains itself |

Two refusals worth wording carefully:

- **The shareware episode.** It is legally redistributable — every BBS in 1992 did — and it is
  still not freely *licensed* in the sense Freedoom and Blasphemer are, so it does not enter
  the catalogue (addendum 06 permits freely licensed data only) and this project does not
  offer it, the same line already held for shareware Doom, shareware Quake and the Hexen II
  demo. The refusal names it: "that is the shareware episode; this gate runs the registered
  game."
- **Spear of Destiny.** Not a map pack over Wolfenstein — a sibling game on the same engine,
  selected at compile time in Wolf4SDL. Supporting it means a second module behind the same
  gate or a variant switch in the platform layer, and that decision deserves measurements
  from the built gate first. Item 60 holds the question; until then the reading names the
  files exactly so an owner knows the app knows.

No free replacement exists to offer. If one ever matures, it slots into the catalogue the way
Freedoom did, with no change to the gate.

### The shelf

`slipgate-server` gains a `grosse/` folder holding the eight `.WL6` files, and
`tooling/data-shelf/inspect-shelf.py` learns the set-of-siblings reading — the first entry
kind where the unit of validation is a directory's worth of files rather than one file. The
manifest carries one entry per file with the set named, so a partial copy is visible as
exactly that. Same rules as the app, same vocabulary, item 54.

## What is inherited and not built again

| Already delivered | Phase 9 uses it as-is |
|---|---|
| 31 — dual-target platform layer shape, `SG_EXPORT`, `replaced-sources.txt` | The build pattern; `platform-wolf/` is its third instantiation |
| 32 — dlopen/JNI bridge, zero-copy framebuffer | Unchanged; `libgrosse` is one more `RTLD_LOCAL` handle |
| 33, 34 — iOS static libraries, prefix header, cinterop | The prefix mechanism, verbatim |
| 36 — the `chthon` gate | Module template, harness shape, credits pattern |
| 37 — backend override in Settings | Applies to the new gate for free |
| The web driver | `grosse.wasm` through it, all three platforms in one pull request |

Nothing from phase 8's pak work applies — Wolfenstein has no paks — which is itself the
evidence for the third-family claim: the only sharing is the seam *shape*, not the seam.

## Art

Two files, the same two every painted gate ships, as WebP under
`ui/src/commonMain/composeResources/files/backdrops/`, then `grosse` joins the `painted` set
in `Backdrops.kt`.

| File | Size | Role |
|---|---|---|
| `cover_grosse.webp` | 1024 × 1024 | The gate card in the rack |
| `bg_grosse.webp` | 1920 × 1920 | The full-screen backdrop while the card is focused |

House style as the other six: pixel art, chunky visible dithering, hard-edged clusters, no
airbrush. Wolfenstein's hue is **cold blue-grey stone and gunmetal against banner red**, with
brass and warm lamplight as the metal accents — the castle, not the fire. It sits cleanly
beside `mars` (ember orange) and `macil` (amber and rust) without repeating either.

### Cover prompt — `cover_grosse.webp`

> Pixel art game cover, 1:1 square, 1024×1024, retro 90s DOS aesthetic with visible chunky
> dithering and a tight indexed palette. A hulking armoured soldier fills a stone archway,
> seen from low and slightly below, a chaingun in each fist, cold lamplight glinting off
> blue-grey plate armour. Behind him a castle corridor of huge stone blocks recedes into
> darkness — barred door, hanging chains, a long red banner on the wall reading as plain red
> cloth with no symbol. One warm torch flame against the cold palette. Blue-grey stone and
> gunmetal dominant, banner red as the single strong accent, brass details. Near-black
> ground, high contrast, hard-edged pixel clusters, no soft gradients, no airbrush. An ornate
> rectangular border frame with corner ornaments in tarnished brass and cold steel surrounds
> the whole image. No text, no lettering, no symbols, no insignia, no logo, no watermark, no
> signature.

### Backdrop prompt — `bg_grosse.webp`

> Pixel art scene, 1:1 square, 1920×1920, retro 90s DOS aesthetic with visible chunky
> dithering and a tight indexed palette. Wide establishing shot of a vast castle hall at
> night: massive blue-grey stone block walls, rows of square pillars, barred iron doors, long
> plain red banners hanging between wall-mounted torches whose warm light pools on a stone
> floor. A single tiny armoured figure stands far down the hall, dwarfed by the architecture,
> reading as a silhouette. Drifting dust caught in torchlight. Cold blue-grey and gunmetal
> against banner red and brass. Composition centre-weighted, the upper quarter and lower
> quarter deliberately dark and empty so interface text stays legible over them. Deep
> near-black ground, high contrast, hard-edged pixel clusters, no soft gradients, no
> airbrush. No border, no frame — the image bleeds to all four edges. No text, no lettering,
> no symbols, no insignia, no logo, no watermark, no signature.

The banners are deliberately plain red cloth. The game's own iconography is its owner's
trademark and history's worst logo; the art wants the castle and the dread, not the symbol.
Both prompts say so twice.

## Delivery plan

Inserted items continue the numbering, per §F. One pull request each, micro commits inside.

| Item | Scope | Depends on |
|---|---|---|
| 53 | `build(tooling)`: mirror Wolf4SDL, pin it in `SOURCES.lock` and `fetch-sources.sh` | — |
| 54 | `feat(host-gamedata)`: recognise Wolfenstein data sets — signatures, variants, versions, the set-shaped requirement, the shelf tool | — |
| 55 | `build(tooling)`: the Wolf platform layer — third family — and `grosse.wasm`; the OPL music decision | 53 |
| 56 | `build(tooling)`: native `libgrosse` for Android, iOS and the build host | 55 |
| 57 | `feat(games-grosse)`: the gate on all three platforms, determinism harness, credits | 36, 54, 55, 56 |
| 58 | `feat(design-system)`: `grosse` cover and backdrop | — |
| 59 | `docs`: LICENSE-NOTES gains the 1995 licence category; README gains the gate row and the iOS sentence | 53 |
| 60 | `feat(games-grosse)`: Spear of Destiny — conditional, decided on 57's measurements | 57 |

The critical path is 53 → 55 → 56 → 57. Items 54 and 58 run parallel to it; 59 lands any time
after 53; 60 only exists if the answer to its question is yes.

Phase 9 sits behind phases 7 and 8 by design: 55 wants the platform-layer conventions phase 8
finishes settling, 56 wants 33's prefix mechanism proven twice, and 57 wants 36's module
template. Starting earlier would mean drawing a third seam while the second is still wet.

Specifics the implementing pull requests hold to:

- The build is Wolf4SDL's game code with its SDL layer replaced entirely by `platform-wolf/`
  — no SDL, no SDL_mixer, exactly as no gate before it ships SDL. The variant is the
  registered game; `UPLOAD`-style shareware builds are out.
- The frame is 320×200, 8-bit indexed. Palette shifts report through `SG_PALETTE_CHANGED`.
- The clock feeds 70 Hz tics through the engine's own adaptive `CalcTics`; the host steps and
  the engine decides how many tics it owes, Hexen II's arrangement at a different rate.
- Saves are flat `SAVEGAM?` files through the existing seam — they fit `MAX_FILES` without
  the directory work Hexen II needed.
- The determinism harness replays an attract demo against itself when the port's demo path
  makes that possible, and otherwise runs one map twice from a fixed clock with no input,
  hashing the framebuffer each step. `US_RndT` draws from a fixed 256-entry table, so the
  sequence is the same on every run — the proof is real either way. Gated on a
  `-Pslipgate.wolfdata` property; skips without real data; and the gate is not believed to
  work until it has booted against real data on a device, which is the lesson #50 cost.
- Module memory is sized from a measurement. It will be the smallest number in the table, and
  writing 128 MiB out of habit would be the habit, not the measurement.

## Attribution

§G gains two entries, landing with item 57 on the credits screen: **Wolf4SDL** and Moritz
"Ripper" Kroll, and **id Software's 1995 Wolfenstein 3D source release** — the code that,
before the GPL releases existed, taught this codebase's whole lineage what a source release
was for.

## Open questions

- **Does the OPL2 emulator hold the budget on the Chasm interpreter?** It runs per audio
  sample, not per frame, which is a different cost shape from everything measured so far.
  Item 55 measures it; the fallback is effects-only, said honestly.
- **Does `.WL3` boot the registered build?** Wolf4SDL selects variants at compile time; how
  much of that is genuinely compile-time and how much can become a data-driven check is a
  reading of the source, not a guess in a document. Item 54 answers it and the data table
  above defers to that answer.
- **Is Spear of Destiny a second module or a variant switch?** Compile-time defines say
  second module; the size of the game code says a switch might be honest. Item 60's question,
  answered with 57's measurements in hand.
- **What does the smallest engine cost per step on a phone?** Unmeasured like everything
  until it runs, but this is the one gate with a real chance of holding 70 Hz on the
  interpreter alone. The README table gains its row either way, from `FrameBudgetTest` and a
  device run like every other gate's.
