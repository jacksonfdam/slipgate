# The home library scripts

Two scripts that let a player serve their own game data from a NAS to every device they play on.
The setup guide, including the site half of it, is [docs/home-library.md](../../docs/home-library.md).

| Script | What it is |
|---|---|
| `slipgate-library.py` | The file server. Standard library only, listens on localhost, refuses every request without the key, answers `Range` and cross-origin requests |
| `slipgate-library.sh` | The boot script. Starts the server, opens a `cloudflared` or `ngrok` tunnel, reads the public hostname back out of it, and publishes that to the beacon |

## Quickly

```bash
export SLIPGATE_LIBRARY_ROOT=/volume1/games/slipgate
export SLIPGATE_LIBRARY_KEY=$(openssl rand -hex 16)
./slipgate-library.sh
```

With no `SLIPGATE_BEACON_URL` it prints the address rather than publishing it, which is enough to
paste into one device's Settings and try the whole path once.

## The two documents

The launcher reads both with a `split`, because the host carries no JSON parser. A line whose first
field is unrecognised is ignored rather than refused, so a later version can add one.

The pointer, published to the beacon by the boot script:

```
slipgate-beacon 1
url	https://some-hostname.trycloudflare.com
key	9f0c1d2e3a4b5c6d7e8f9a0b1c2d3e4f
updated	2026-08-20T18:22:41Z
```

The manifest, served at `/manifest` and derived from the directory itself:

```
slipgate-library 1
file	mars	doom.wad	game	14604584	/files/mars/doom.wad
file	mars	sunlust.wad	addon	18324	/files/mars/addons/sunlust.wad
```

Fields are `file`, the gate directory, the file name, `game` or `addon`, the size in bytes, and the
path to fetch it from.

## Endpoints

| Path | Answer |
|---|---|
| `GET /health` | `slipgate-library ok`, without a key, so a supervisor can wait on it |
| `GET /manifest` | The manifest above |
| `GET /files/<gate>/<name>` | The file, with `Range` and `HEAD` supported |

Everything but `/health` needs the key, as `?key=…` or as a bearer token.
