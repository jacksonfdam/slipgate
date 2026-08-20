# Addendum 07 — the shelf beyond the LAN

Amends [08-addendum-06.md](08-addendum-06.md) with the case it does not cover, and
[01-foundation.md](01-foundation.md)'s acquisition flow with one setting. Everything else stands,
including every non-negotiable rule: no game data in the repository, in releases, or in CI caches;
nothing in `host/*` names a game module; the launcher fetches only what a player asked it to fetch.

## The case addendum 06 does not cover

Addendum 06 describes `slipgate-server`: an origin at a stable address, a public catalogue of freely
licensed data and a private per-owner locker, a JSON manifest at `/catalogue/v1.json`, a bearer
token. [docs/data-shelf.md](../data-shelf.md) is its local half — the same directory, served on the
LAN by `serve-shelf.py`, where the network is the authentication.

Between those two is the case this document is about: **the shelf a player already has, reached from
outside their network, at an address that moves.**

A NAS behind a home tunnel is not a hosted origin. A quick `cloudflared` or `ngrok` tunnel gets a new
hostname every time it starts, so there is no stable base URL to configure, and configuring one per
device after every power cut is worse than the file picker it replaces. Nothing in addendum 06
addresses that, because a hosted origin never moves.

Two pieces close it, and both are small:

- **A key on the shelf server.** `serve-shelf.py` gains `--key`, off by default so LAN use is
  unchanged. With it set, every path but `/health` needs the key. This is not optional once a tunnel
  is involved: a tunnel hostname is the only thing between the public web and a directory of retail
  game data, and a hostname is not a secret.
- **A beacon.** `publish-shelf.sh` boots the shelf on localhost, opens the tunnel, reads the public
  hostname back out of it, and publishes that hostname and the key to one address that never changes.
  The app is configured with the beacon; the NAS keeps it current. The beacon is `site/`, the first
  thing in this repository that is deployed rather than built.

## What the app gains

One setting — `SlipgateSettings.shelfAddress`, in Settings → Data shelf — and one route on a gate's
data screen. Which kind of address was configured is decided by what answers rather than by asking
the player to declare it: a beacon answers with a pointer, a shelf answers with an index.

`RemoteShelf` in `host/gamedata` reads both documents and hands the existing acquisition the URL to
fetch. Nothing else changes: a file from a shelf is inspected, refused or stored exactly like one
picked by hand, under the key the gate looks for, so a gate boots from an IWAD the NAS calls
something else. A gate on a train works because every route ends in the same local storage.

## Three deliberate differences from addendum 06

Each has a reason and a way it converges. None of them touches the catalogue, and none of them
loosens a validation.

- **The index is tab separated lines, not JSON.** Addendum 06's manifest is the client model
  serialised, which is right for a hosted origin with a generator behind it. `/shelf.index` is
  derived from `manifest.json` — the readings `inspect-shelf.py` wrote stay the source of truth — and
  flattened to lines because the host carries no serialisation library and one added for six fields
  would be the largest dependency in `host/gamedata`. When item 46 lands a JSON manifest client, the
  shelf serves that schema and the line format becomes legacy.
- **The key travels in the query, not in a header.** `DataDownload.fetch(url, onProgress)` carries no
  headers on any of the three platforms today, and item 47 is the change that rewrites that interface
  for resume. A bearer token belongs in that change rather than ahead of it; the server already
  accepts `Authorization: Bearer` for whichever arrives first.
- **`DataSource` is unchanged.** Addendum 06 adds `OwnedOrigin(key)` so a gate can declare the origin
  as a route. A shelf needs no such case: what it offers is discovered from its index, so the route
  appears without a gate declaring it. When item 46 adds the case, a shelf's files should be listed
  through it rather than beside it.

## Delivery

One pull request, micro commits inside.

| Commit | Scope |
|---|---|
| 1 | `feat(tooling)`: the key and the index on `serve-shelf.py`, and the publisher that tunnels it |
| 2 | `feat(site)`: the beacon, and the page that explains it |
| 3 | `feat(host-gamedata)`: the two documents, and the client that reads them |
| 4 | `feat(launcher)`: the address in Settings, and the shelf route on a gate's data screen |
| 5 | `docs`: the guide, and this addendum |

## What is left for later

- **Add-ons from a shelf.** The index lists them and `ShelfListing.addOns` returns them; the Settings
  shelf still installs map packs from a file picker only.
- **A checksum check on arrival.** `manifest.json` already carries a `sha256` per file and the index
  drops it. Item 46 is where it should be carried through and verified, as a transport check rather
  than as a trust root — the inspector stays the authority.
- **Uploads.** Addendum 06's item 49 puts an installed file back on the origin. A shelf on hardware
  the player owns is the easiest place for that to be true, and it needs a write path this does not
  have.
