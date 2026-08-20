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

No authentication by default, deliberately: it serves your own network from your own machine. Pass
`--bind` your LAN address rather than `0.0.0.0` if that distinction matters where you are, and do
not port forward it. Reaching it from outside your network is what the next section is for, and it
is where a key stops being optional.

### What the app reads

```
GET /health        `slipgate-shelf ok`, no key needed, so a supervisor can wait on it
GET /shelf.index   what is here, as lines
GET /<shelf>/<name>  the file itself, with Range and CORS
```

`/shelf.index` is tab separated lines under a version header, because the host carries no JSON
parser and adding one for six fields would make it the largest dependency in `host/gamedata`:

```
slipgate-shelf 1
file	mars	doomu.wad	game	12408292	/mars/doomu.wad
file	chthon	pak0.pak	game	18689235	/chthon/id1/pak0.pak
file	addons	hexdd.wad	addon	4429680	/addons/hexdd.wad
```

The fields are `file`, the shelf a file sits in, its name, `game` or `addon`, its size in bytes, and
the path to fetch it from. It is derived from `manifest.json` when `inspect-shelf.py --manifest` has
written one — the readings there are the source of truth — and from the directory itself when it has
not, so a shelf nobody has inspected yet still installs. When addendum 06's JSON manifest client
lands, this becomes the legacy format.

## Reach it from outside your network

A shelf on the LAN covers the devices at home. The rest of the time — a phone on mobile data, a
laptop somewhere else — the shelf has to be reachable from the public web, and two things follow
immediately: it needs a key, and its address moves.

```bash
tooling/data-shelf/publish-shelf.sh
```

That script does three things in order, and the order is the design:

1. `serve-shelf.py` comes up on **localhost only**, with a key. A tunnel hostname is the only thing
   between the public web and a directory of retail game data, and a hostname is not a secret.
2. A tunnel — `cloudflared` or `ngrok` — puts it on the web over TLS. A quick tunnel gets a new
   hostname every time it starts.
3. That hostname and the key are published to a **beacon**, so every device is configured with one
   address that never changes. The beacon is the `site/` project in this repository; deploying it is
   [site/README.md](../site/README.md).

| Variable | Default | Meaning |
|---|---|---|
| `SLIPGATE_SHELF_ROOT` | `slipgate-server` | The shelf to serve |
| `SLIPGATE_SHELF_PORT` | `8600` | The port, bound to localhost |
| `SLIPGATE_SHELF_KEY` | generated | The key every request carries. Generated into the state directory and kept if unset |
| `SLIPGATE_TUNNEL` | `cloudflared` | `cloudflared`, `ngrok`, or `none` when something else already exposes it |
| `SLIPGATE_PUBLIC_URL` | — | Where the shelf is reachable, for `none` and for a named cloudflared tunnel |
| `SLIPGATE_CLOUDFLARED_TUNNEL` | — | A named tunnel to run instead of a quick one, which keeps its hostname |
| `SLIPGATE_BEACON_URL` | — | The beacon to publish to. Unset, the script prints the address instead |
| `SLIPGATE_BEACON_TOKEN` | — | The beacon's publish token |
| `SLIPGATE_REPUBLISH_SECONDS` | `900` | How often the pointer is refreshed |
| `SLIPGATE_STATE_DIR` | temporary directory | Where the logs and the generated key are kept |

It stays in the foreground, republishing on a timer, and exits when either the shelf or the tunnel
dies so that whatever supervises it starts it again. A systemd unit or a Synology scheduled task
running at boot is enough.

The pointer it publishes is four lines:

```
slipgate-beacon 1
url	https://some-hostname.trycloudflare.com
key	9f0c1d2e3a4b5c6d7e8f9a0b1c2d3e4f
updated	2026-08-20T18:22:41Z
```

### In the app

Settings → Data shelf → **Beacon or shelf address**. Paste either the beacon address or the shelf
itself — on the LAN that is `http://192.168.x.x:8600`, and with a key `…:8600?key=…`. Which one it
is, is decided by what answers rather than by asking. The line underneath says what was found, and
a gate that needs data then offers **Install *file* from my shelf** above its other routes. The
address is asked once as the app opens and again whenever it changes; a shelf that was off at
startup is the ordinary case, so there is a **Check again**.

Files arriving from a shelf are inspected exactly like files picked by hand. Nothing is trusted
because a shelf said it.

### What is protected, and what is not

- **The key is checked in constant time**, and a missing key and a wrong key get the same answer.
- **The key travels in the query string.** The three platform HTTP clients behind the app's one
  `fetch(url)` carry no headers until addendum 06's item 47 rewrites that interface for resume; the
  server already accepts `Authorization: Bearer` for whichever lands first. It is inside TLS, and it
  does reach the NAS's own log. Rotating it is changing `SLIPGATE_SHELF_KEY` and restarting, which
  republishes the pointer.
- **The beacon id is a credential, not a name.** Whoever holds it can read the pointer and so reach
  the shelf. It is 24 to 64 hex characters for that reason, and the pointer is stored under a path
  derived from the id *and* the publish token.
- **This is not a way to share game data.** Serving retail IWADs and paks to people who did not buy
  them is redistribution however short-lived the tunnel is. The shelf is yours; keep the beacon id
  to your own devices.

The decision record, including where this deliberately differs from addendum 06, is
[specification/09-addendum-07.md](specification/09-addendum-07.md).

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
