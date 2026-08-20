# The local data shelf

One folder on your own network holding the games you own, laid out the way the engines expect, so
every device you test on reads the same copy. Nothing about it is tracked and nothing about it
ships: `.gitignore` has `slipgate-server/`, and the file extensions above it cover the data
itself. What is tracked is the three scripts that create, read and serve it.

This is the local half of [the data origin](specification/08-addendum-06.md). That document
describes an origin facing the internet, with a public catalogue and an authenticated locker; this
is the same idea on a LAN, where the network is the authentication.

## The layout

```
slipgate-server/
├── README.md          written by hand: what is on the shelf and why
├── manifest.json      every file's reading, size and sha256
├── mars/              doomu.wad, doom.wad, doom2.wad, plutonia.wad, tnt.wad, chex.wad
├── corvus/            heretic.wad
├── korax/             hexen.wad
├── macil/             strife1.wad, voices.wad — once the Strife gate exists
├── chthon/id1/        pak0.pak, pak1.pak
├── eidolon/
│   ├── data1/         pak0.pak, pak1.pak, demo1.dem …
│   └── portals/       pak3.pak — Portal of Praevus
├── addons/            files that load over a game rather than booting one: hexdd.wad
└── unsupported/       valid data no gate runs yet
```

One shelf per gate, named for the gate. The two Quake-family gates keep the engine's own
directory names inside their shelf, so a Quake or Hexen II install folder copies straight in —
`id1/`, `data1/` and `portals/` are the directories those engines look for, and a gate passes
`-portals` rather than a file list for the mission pack.

```bash
./tooling/data-shelf/init-shelf.sh
```

Creates or repairs the directories. It never touches data, never moves anything, and leaves
`README.md` and `manifest.json` alone; the notes it writes into the two pak shelves are skipped if
they already exist. Safe to run whenever a new gate appears.

## Read it

```bash
./tooling/data-shelf/inspect-shelf.py --manifest
```

Reads every file and says what it is, then reports which gates are still short of something.
Names are ignored on purpose: a file called `pak0.pak` that is Hexen II 1.03 is named as 1.03, and
a file called anything at all that is the registered 1.11 pak is accepted.

```
  ✓ mars/doomu.wad
      IWAD, Bootable, DoomEpisodic, 4 episodes, 2306 lumps
  ✓ addons/hexdd.wad
      IWAD, AddOn, no engine named, 26 maps, 326 lumps
  ✗ eidolon/data1/pak0.pak
      PACK, Bootable, HexenII, retail v1.03 — needs Raven's 1.11 patch, 697 files.
      this gate needs the registered 1.11 data

  eidolon: still needs data1/pak1.pak
```

The vocabulary is `WadInspection.kt`'s — kind, role, flavour — so a reading here and a reading in
the app are the same sentence. `--manifest` rewrites `manifest.json` with those readings plus each
file's size and sha256, and it reproduces the app's own numbers: the eleven WADs already on the
shelf regenerate with identical kind, role, flavour, episode, map and lump counts.

What each family gives away:

- **WADs** are named by the lumps only they have. `XLATAB` is Strife, `TINTTAB` with `MAPxx` is
  Hexen, `TINTTAB` with `ExMy` is Heretic. `PLAYPAL` is what separates a game from an add-on,
  which is how Chex Quest boots from a `PWAD` signature and Deathkings does not boot from an
  `IWAD` one. A file with no palette gets no flavour guess at all: `hexdd.wad` is Hexen data with
  `MAPxx` names, and so is a Doom II map pack.
- **Hexen II paks** get an exact release: uHexen2 identifies them by the number of files in the
  pak directory and a CRC-16 of it, and that table is in the port's own `quakefs.c`. Anything not
  in the table is reported as unrecorded rather than guessed at.
- **Quake paks** are named by the episodes they carry, because that is what decides whether the
  data is complete — the file count does not. The registered game is `pak0` plus `pak1` in 1996
  and a single repacked `pak0` in the 2021 re-release, which holds everything the two old paks did
  and adds its own; `mapdb.json` and the weapon wheel are its fingerprints, and a `pak0` carrying
  all four episodes is reported as complete with no `pak1` beside it. Episode 1 alone is what
  `pak0` holds in both 1996 releases, so that reading says where the rest are rather than claiming
  to know which disc it came from.

It exits non-zero when a file is present but unusable, so it fits in a script.

## Serve it

```bash
./tooling/data-shelf/serve-shelf.py
```

Prints the LAN URL to point a device at, on port 8600. Two things it does that
`python3 -m http.server` does not, and they are the reason it exists:

- **CORS.** The browser target fetches data with `fetch()`, and without
  `Access-Control-Allow-Origin` the request is refused before it is made. That missing header on
  GitHub's release assets is exactly why the web build cannot download Freedoom today.
- **Range requests.** A 98 MB pak over a phone's wifi has to be resumable. Python's stock handler
  ignores `Range` and answers `200` with the whole file, which reads as success and is not.

No authentication, deliberately: it serves your own network from your own machine. Pass `--bind`
your LAN address rather than `0.0.0.0` if that distinction matters where you are, and do not port
forward it. The token model in addendum 06 is for an origin that faces the internet.

## Point the tests at it

The gate tests skip themselves without real data, which is how two Heretic bugs once survived into
a merged gate. Give them a path from the shelf:

```bash
./gradlew :games:mars:jvmTest --tests '*RealAcquisitionTest*' -Pslipgate.iwad=slipgate-server/mars/doomu.wad
```

```bash
./gradlew :games:korax:jvmTest --tests '*FrameBudgetTest*' -Pslipgate.iwad=slipgate-server/korax/hexen.wad
```

The Quake and Hexen II gates take `-Pslipgate.pak` the same way once they exist, pointing at
`chthon/id1` and `eidolon/data1`.

Hexen II's determinism harness wants `demo1.dem` beside the paks. It is not inside them — a
uHexen2 installation carries `demo1.dem`, `demo2.dem` and `demo3.dem` as loose files in `data1/`,
and `hexen.rc` ships a commented-out `startdemos demo1 demo2 demo3` for them. Copy them in with
the paks: a demo replayed against itself is a stronger proof than a map run on a fixed clock, and
cheaper to write.
