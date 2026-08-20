# The home library: serving your own game data to every device

Slipgate ships no game data, so every device needs the player's own files. Owning one copy and
supplying it four times — phone, tablet, browser, iPad — is the part of that which nobody enjoys,
and it gets worse away from home, where the NAS holding the files is not on the network the phone
is on.

A home library fixes both. One directory of files on a NAS, one small server in front of it, one
tunnel in front of that, and one address in Settings on each device. After a power cut nothing has
to be retyped: the NAS republishes where it is, and every device follows.

Nothing here downloads anything you do not already own, and nothing here is a public mirror. The
library refuses every request that does not carry its key.

## The parts

| Part | Where it lives | What it does |
|---|---|---|
| `slipgate-library.py` | The NAS | Serves one directory over HTTP, behind a key, with CORS and resumable transfers |
| `slipgate-library.sh` | The NAS | Boots the server, opens the tunnel, reads back the public hostname, publishes it |
| `site/api/beacon/[id].js` | Vercel | Stores the pointer the NAS publishes, and hands it to the app |
| Settings → Home library | The app | The one address a device is configured with |

The beacon is the only part that can be skipped. A player who only ever plays at home can put the
NAS address straight into Settings and never deploy the site; the launcher accepts either, and
decides which it was given by what answers.

## On the NAS

### Lay the files out

One directory per gate, named for the gate, with map packs under `addons`:

```
/volume1/games/slipgate/
  mars/doom.wad
  mars/addons/sunlust.wad
  corvus/heretic.wad
  korax/hexen.wad
  macil/strife1.wad
  macil/voices.wad
```

The directory layout *is* the manifest — nothing has to be kept in step with it by hand. Files
whose extension is not game data are neither listed nor served, so notes and backups in the same
directory stay private.

The gate names are `mars` (Doom), `corvus` (Heretic), `korax` (Hexen) and `macil` (Strife). A file
in the wrong gate's directory is still inspected on arrival and refused by name, so a mistake here
costs a download rather than a broken gate.

### Configure it

The script reads environment variables, which it can take from a file:

```bash
# /volume1/games/slipgate/library.env
SLIPGATE_LIBRARY_ROOT=/volume1/games/slipgate
SLIPGATE_LIBRARY_PORT=8099
SLIPGATE_LIBRARY_KEY=9f0c1d2e3a4b5c6d7e8f9a0b1c2d3e4f
SLIPGATE_TUNNEL=cloudflared
SLIPGATE_BEACON_URL=https://slipgate.example/beacon/2f4c6a8e0b1d3f5a7c9e1b3d
SLIPGATE_BEACON_TOKEN=the-publish-token
```

| Variable | Default | Meaning |
|---|---|---|
| `SLIPGATE_LIBRARY_ROOT` | *required* | The directory laid out above |
| `SLIPGATE_LIBRARY_PORT` | `8099` | The port the server listens on, localhost only |
| `SLIPGATE_LIBRARY_KEY` | generated | The key every request has to carry. Generated into the state directory and kept if unset |
| `SLIPGATE_TUNNEL` | `cloudflared` | `cloudflared`, `ngrok`, or `none` for a library reachable without one |
| `SLIPGATE_PUBLIC_URL` | — | Where the library is reachable, for `none` and for a named cloudflared tunnel |
| `SLIPGATE_CLOUDFLARED_TUNNEL` | — | A named tunnel to run instead of a quick one, which keeps its hostname |
| `SLIPGATE_BEACON_URL` | — | The beacon to publish to. Unset, the script prints the address instead |
| `SLIPGATE_BEACON_TOKEN` | — | The beacon's publish token |
| `SLIPGATE_REPUBLISH_SECONDS` | `900` | How often the pointer is refreshed |
| `SLIPGATE_STATE_DIR` | temporary directory | Where the logs and the generated key are kept |

Generate the key and the beacon id with `openssl rand -hex 16` each. Both are credentials: the key
opens the library, and the beacon id is what lets a device read the pointer holding that key.

### Run it

```bash
tooling/library/slipgate-library.sh --config /volume1/games/slipgate/library.env
```

It stays in the foreground, republishing on a timer, and exits when the server or the tunnel dies
so that whatever supervises it can start it again. On systemd:

```ini
[Unit]
Description=Slipgate home library
After=network-online.target

[Service]
ExecStart=/volume1/games/slipgate/slipgate/tooling/library/slipgate-library.sh --config /volume1/games/slipgate/library.env
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

On a Synology without systemd access, Control Panel → Task Scheduler → Create → Scheduled Task,
run at boot, with the same command. Python 3 is already present on DSM 7; `cloudflared` or `ngrok`
has to be installed, either as a package or inside a container.

Requirements are Python 3.9 or newer, `bash`, `curl`, and whichever tunnel you chose. There are no
libraries to install.

## The beacon

Deploy `site/` to Vercel as its own project, with the root directory set to `site`. It needs a Blob
store, which Vercel provisions with its own token, and one environment variable of your own:

```bash
vercel blob store add slipgate-beacon
vercel env add SLIPGATE_BEACON_TOKEN production
```

Then the beacon answers at `/beacon/<id>`:

| Request | Who | Effect |
|---|---|---|
| `POST /beacon/<id>` | The NAS | Stores the pointer. Needs the publish token |
| `GET /beacon/<id>` | The app | Returns the pointer, uncached |
| `DELETE /beacon/<id>` | You | Forgets it, so devices stop being sent anywhere |

A pointer is four lines:

```
slipgate-beacon 1
url	https://some-hostname.trycloudflare.com
key	9f0c1d2e3a4b5c6d7e8f9a0b1c2d3e4f
updated	2026-08-20T18:22:41Z
```

The site's own page checks a beacon without showing the key, which is enough to answer "is the NAS
up and did it announce itself" from a phone.

## In the app

Settings → Home library → **Beacon or library address**. Paste either the beacon address or the
library itself; on the keyboard's done action the launcher reaches it and the line underneath says
what answered. From then on, a gate that needs data offers **Install *file* from my library** above
its other routes, and the file is validated on arrival exactly like one picked by hand.

The address is asked once as the app opens and once whenever it changes. A NAS that was off at
startup is the ordinary case rather than an error: turn it on and press **Check again**.

## What is protected, and what is not

- **The library refuses anonymous requests.** The key is checked in constant time, and a missing key
  and a wrong key get the same answer.
- **The key travels in the query string.** The three platform HTTP clients behind the launcher's one
  `fetch(url)` carry no headers today, so this is the version that works on all three. It is inside
  TLS, and it does reach the NAS's own log; rotating it means changing `SLIPGATE_LIBRARY_KEY` and
  restarting, which republishes the pointer.
- **The beacon id is a credential, not a name.** Anyone holding it can read the pointer and so reach
  the library. It is 24 to 64 hex characters for that reason, and the pointer is stored under a path
  derived from the id *and* the publish token, so knowing the id is not enough to reach the stored
  document directly.
- **The tunnel hostname on its own opens nothing.** A quick tunnel hostname is guessable in
  principle; without the key it serves 401 to every path.
- **This is not a way to share game data.** Serving commercial IWADs to people who did not buy them
  is redistribution however short-lived the tunnel is. Keep the beacon id to your own devices.

## Why the formats are lines rather than JSON

Both documents — the pointer and the manifest — are tab separated lines under a version header. The
host carries no serialisation library, and adding one so that four fields could be read would make
it the largest dependency in `host/gamedata`. A format a `split` can read is also a format that
cannot half-parse, and it can be written by hand with `printf` when something has gone wrong at two
in the morning.

## What is not built yet

- Installing add-ons from the library. The manifest already lists them and the launcher already
  separates them from bootable data, but Settings still adds map packs from a file picker only.
- A gate reading directly from the library without storing the file first. Every route stores what
  it fetched, which is what makes a gate work on a train.
