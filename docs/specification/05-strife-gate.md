# The Strife gate

Working notes for the fourth gate, written before the work so the decisions were arguable
while they were still cheap.

**The gate is built.** It boots, and the plan below is kept as written rather than tidied
into hindsight — a plan edited to match what happened stops being evidence of anything. What
the plan got right, got wrong and did not see is recorded at the end.

## Why it is the next gate

Chocolate Doom 3.1.1 — the tree Slipgate already builds from, pinned in `SOURCES.lock` —
ships `chocolate-strife` alongside the three engines already wired. The engine exists, the
toolchain that turns it into a `.wasm` module exists, and the gate contract it would
implement has not changed since Hexen. What is missing is a module directory, a key map,
art, and the platform shim under `tooling/engine-build/platform/`.

It also closes a hole this repository currently has. Until [04-addendum-03.md](04-addendum-03.md)
the Doom gate accepted `strife1.wad` as Doom data and then could not boot it, because
Strife is Doom's engine with `MAPxx` naming and nothing in the old inspector separated the
two. That is now caught and refused with Strife named in the message — which is honest, and
also an invitation. A player who owns Strife is told the app knows what the file is and
will not run it.

## The name

`macil`, after the rebel leader — following the pattern the other gates set: `mars` is
Doom's setting, `corvus` is Heretic's protagonist, `korax` is Hexen's antagonist, `chthon`
is Quake's. Hard, mythic, two syllables.

`blackbird` — the voice in the player's ear for the whole game — is the more recognisable
Strife name and the better metaphor for a gate, but it does not sit with the others.

Worth settling before the first commit: the id reaches storage keys, the `gate:` label and
any deep link, so it is cheap now and annoying later.

## What Strife needs that the other three did not

- **Dialogue.** Strife has conversations with branching responses and a currency. That is
  more input surface than any existing gate: number keys select a response, and the
  inventory is its own screen. The input profile grows accordingly, and the virtual gamepad
  has to draw it.
- **A second data file.** `voices.wad` holds the voice acting and sits beside `strife1.wad`
  rather than replacing anything in it. It is optional — the game runs silent-but-subtitled
  without it — so it belongs in the requirements as an entry the gate can boot without,
  which is a shape `DataRequirements` does not currently express. Either it gains an
  `optional` flag or the voices install as an add-on, which is what the add-on shelf is
  already for.
- **No free replacement.** There is no Freedoom for Strife. The card says user-supplied
  only, the way Hexen's already does.
- **Its own translucency table.** `XLATAB` rather than Raven's `TINTTAB`; the inspector
  already reads it.

## Art

Two files, the same two every painted gate ships, committed as WebP under
`ui/src/commonMain/composeResources/files/backdrops/`:

| File | Size | Role |
|---|---|---|
| `cover_macil.webp` | 1024 × 1024 | The gate card in the rack |
| `bg_macil.webp` | 1920 × 1920 | The full-screen backdrop while the card is focused |

Then `macil` joins the `painted` set in `Backdrops.kt`, which is all the wiring there is.

### House style

Every existing cover and backdrop shares this, and a fourth that misses it will look
pasted in:

- **Pixel art**, visible chunky dithering, hard-edged clusters, no soft airbrush gradients.
- **A tight palette in one dominant hue** taken from the game's own: mars is ember red and
  orange, corvus is verdigris green, korax is violet and bone, chthon is molten orange over
  brown. The launcher then samples its accent from the game's real palette and the two
  agree.
- **A deep, near-black ground.** These are read behind a scrim with text over them.
- **Covers carry an ornate border frame** with corner ornaments, drawn in the gate's hue.
  **Backdrops carry no frame** — they bleed to the edge, sit darker overall, and keep the
  top and bottom quieter, because the shell scrims both and puts the rail, wordmark and
  rack over them.
- **A figure or icon holds the centre**, environment behind it, one dramatic light source.

### Strife's hue

Amber and rust against gunmetal and dark teal. Strife is the one game of the four that is
science fiction dressed as fantasy — a ruined industrial town under a theocracy — so the
palette should read as oxidised metal and sodium light rather than hellfire or magic.

### Cover prompt — `cover_macil.webp`

> Pixel art game cover, 1:1 square, 1024×1024, retro 90s DOS aesthetic with visible chunky
> dithering and a tight indexed palette. A hooded rebel fighter stands centre, seen from
> behind and slightly below, silhouetted against a ruined cathedral-fortress of riveted
> iron and stone. They hold a crossbow at their side; a small mechanical bird perches on
> one shoulder, one glowing eye. Above the fortress a vast armoured Order sentinel looms in
> silhouette, a single amber optic burning through the smog. Sodium-amber light rakes from
> the left across gunmetal and rust; deep teal shadow fills the rest. Smokestacks, hanging
> chains, and a tattered banner bearing a stylised eye. Near-black ground, high contrast,
> hard-edged pixel clusters, no soft gradients, no airbrush. An ornate rectangular border
> frame with corner ornaments in tarnished brass and dark teal surrounds the whole image.
> No text, no lettering, no logo, no watermark, no signature.

### Backdrop prompt — `bg_macil.webp`

> Pixel art scene, 1:1 square, 1920×1920, retro 90s DOS aesthetic with visible chunky
> dithering and a tight indexed palette. Wide establishing shot of a rain-slick town square
> under theocratic occupation: riveted iron walls, a raised stone gantry, barred windows
> lit sodium amber, a colossal cathedral of industry filling the far background with a
> single burning amber eye set high in its facade. Small hooded figures scattered at the
> lower third, dwarfed by the architecture, reading as silhouettes. Amber and rust against
> gunmetal and deep teal; drifting smog and falling rain caught in the light. Composition
> centre-weighted, with the upper quarter and lower quarter deliberately dark and empty so
> interface text stays legible over them. Deep near-black ground, high contrast, hard-edged
> pixel clusters, no soft gradients, no airbrush. No border, no frame — the image bleeds to
> all four edges. No text, no lettering, no logo, no watermark, no signature.

### After generating

- Export WebP. The existing files run 130–430 KB; anything much larger is over-encoded.
- Check both against `bg_settings` and `bg_credits` in the shell, not in isolation. The
  test is whether the rail and wordmark stay readable over the backdrop.
- The cover is seen small in the rack far more often than large. Squint at it at 120 px: if
  the silhouette does not read, the composition is too busy.

## Order of work

1. `build-strife.sh` and `platform/strife/` — the module has to exist before anything can
   boot it. Heretic's shim is the closer template; it needed a text screen the Doom one did
   not, and Strife needs one too.
2. The `games/macil` module: gate descriptor, requirements, key map, input profile.
   Corvus is the template, since it is the gate that already declares controls beyond
   Doom's.
3. Optional-data support, or `voices.wad` as an add-on. Decide which before writing either.
4. Art, and `macil` into the `painted` set.
5. The `gate: macil` label, and the path-based automation entry that applies it.

## What actually happened

The order above held. Three things it did not see, and two it called wrong.

### Strife globs a directory the module does not have

The one that would have cost a day if it had surfaced later. The gate died before its first
frame with `ClearTmp: Couldn't open dir`: Strife globs its temporary save directory at
start-up and calls `I_Error` when the glob comes back null, and upstream's `i_glob.c` is
`opendir` and `readdir` over a filesystem the module does not have.

`platform/strife/sg_glob.c` reads the module's own file table instead, treating a directory
as a prefix — any path inside the save area globs successfully whether or not a file is under
it, because an empty temporary directory is the normal state at start-up rather than an error.

Replaced for Strife alone, through `SG_REPLACED_EXTRA`. Every engine calls glob and only
Strife treats failure as fatal, so replacing it for all four would rebuild three modules to
serve a fourth. That matters more than it looks: the build does not reproduce byte for byte
across machines even at the pinned Emscripten, so three unexplainable module diffs would have
been unreviewable.

### `-nograph` is not optional

`showintro` defaults to on and gates `I_InitGraphics` in both `initStartup` and `D_DoomLoop`.
The host escapes start-up inside the engine's first `I_StartFrame`, by which point graphics
must already be up — so with the intro on, the gate never opens. Clearing it also routes
`initStartup` through `TXT_Init`, which the platform's absent text screen declines, exactly
the path Heretic takes.

That needed `engineArguments` to carry a per-gate switch, which it had no way to do.

### `TXT_GetXY` and the wrong header

Strife reads the text cursor back where Heretic only ever set it, so the vendored header
needed the declaration. And the shim's includes are not Heretic's: `players`, `consoleplayer`,
`singledemo` and `G_DeferedPlayDemo` come from `doomstat.h` and `g_game.h` here.

### Called wrong: the dialogue

The plan expected branching dialogue to be the input problem. It is not, yet — the conversation
screen takes number keys the engine already reads, and nothing about it needed declaring. The
real input problem was quantity: eleven extensions against Heretic's five, more than a phone
screen can carry, which is a layout decision rather than a plumbing one. Five reach the pad.

### Called wrong: which keys

The plan said Corvus was the template. It is, for shape — and a trap for numbers. Strife
overrides `key_invleft` and `key_invright` to Insert and Delete where Raven uses the bracket
pair, so copying the table would have compiled and bound two keys Strife does nothing with.
`MacilGateTest` asserts those two codes for that reason.

### Settled

- **The name** is `macil`. The art was generated to it before the module existed, which made
  the choice cheaper to keep than to revisit.
- **`voices.wad`** is an optional `DataEntry` rather than an add-on. The add-on route was
  considered and rejected on a fact the plan did not have: the inspector refuses `voices.wad`
  because it holds no maps, so the shelf could only have taken it by loosening the rule that
  keeps anything at all from being installed as an add-on.
- **Memory** is 128 MiB to start, not the 96 the plan floated.
- **Not on the web**, for the reason `corvus` and `korax` are not.

### Still open

- Recording and playing demos, which the other gates have and this one has not been given.
- Which of the six unmapped extensions deserve a place once the touch layout is revisited.
- A frame budget measurement, so this gate sits in the table in the README with the others.
