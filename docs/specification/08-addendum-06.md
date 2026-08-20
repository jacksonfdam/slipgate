# Addendum 06 — the data origin

Amends [01-foundation.md](01-foundation.md)'s game-data rules with a delivery path, and the
phrase "user-supplied" everywhere it appears. Amends nothing about what may be shipped: the
repository still contains no game data, and neither do its releases or its CI caches.

`slipgate-server` is a separate repository and a separate deployment. This document is the half
that lives here: what the app asks of an origin, what an origin is allowed to answer with, and
what changes in the client.

## The problem it solves

Two, and they are different problems that happen to have one answer.

**The web cannot download the free replacements.** GitHub's release assets send no
`Access-Control-Allow-Origin`, so the browser refuses the request before it is made. The README
has said so plainly for a while, and the fix has always been "serve the data from an origin that
allows it", which is a hosting decision rather than a code one. This is that decision.

**A player's own copy does not travel.** A file picker puts an IWAD on one device. Hexen II's
paks are 98 MB together; Quake's are 52 MB; doing that by hand on a phone, then again on a
laptop, then again in a browser, is the least pleasant part of owning the game. An origin the
player controls turns that into one upload.

## The line that does not move

An origin serves two kinds of thing and must never confuse them.

**The catalogue is public, and holds only what is freely licensed to redistribute.** Freedoom and
Blasphemer, today. Anything added later has to arrive with a licence that permits it, named in
the manifest, and the manifest entry is where that claim is auditable.

**The locker is private, per owner, and holds the owner's own files.** Retail game data — Quake's
`pak0`/`pak1`, Hexen's IWAD, Hexen II's 1.11 paks, Strife, Deathkings, Portal of Praevus — is
never in the catalogue, under any framing. It reaches a device from a locker the player
authenticated into, holding a copy they uploaded themselves. That is a personal file store, and
the distinction from a mirror is not a technicality: nothing is served to anyone who did not put
it there.

Two things stay refused outright, and are worth naming so nobody has to re-derive them:

- **Shareware and demo data is not in the catalogue either.** Quake's shareware `pak0` and Hexen
  II's demo `pak0` are widely mirrored and arguably redistributable, and this project has never
  offered the shareware Doom WAD either. If that changes it changes here, with the licence quoted
  in this document, not quietly in a manifest.
- **Raven's 1.11 patch is not hosted in any form.** Its deltas are derived from the retail data.
  A file derived from game data is game data.

## What "user-supplied" now means

Supplied by the player: through the file picker, or from their own locker. That is the whole
amendment. It does not loosen a single validation:

- Files are still identified **by contents**, never by filename or by anything the origin said.
- Every strict version policy stands exactly as written — `chthon` still needs both Quake paks,
  `eidolon` still needs both Hexen II paks at version 1.11, and a locker that hands over the 1.03
  paks gets the same named refusal a file picker would.
- A `sha256` in a manifest is a transport check — it proves the bytes arrived intact. It is not
  the trust root for what the file *is*. The inspector remains the authority, and it runs on
  locker downloads exactly as it runs on picked files.

## Shape of the origin

Static objects and a manifest. No application server in the request path for content, because
content is the expensive part and the least interesting.

```
GET  /catalogue/v1.json                    the public manifest
GET  /catalogue/<sha256>/<name>            public content, content-addressed
GET  /locker/v1.json                       the caller's own manifest (authenticated)
GET  /locker/<sha256>/<name>               the caller's own content (authenticated)
PUT  /locker/<sha256>/<name>               upload (authenticated)
```

A manifest entry:

```json
{
  "key": "freedoom1.wad",
  "gate": "mars",
  "displayName": "Freedoom: Phase 1",
  "url": "/catalogue/9f86d0…/freedoom-0.13.0.zip",
  "archiveEntry": "freedoom1.wad",
  "bytes": 39124992,
  "sha256": "9f86d0…",
  "licence": "BSD-3-Clause, freedoom/freedoom COPYING"
}
```

`key`, `displayName` and `archiveEntry` are the fields `DataEntry` and `DataSource.FreeDownload`
already carry, which is deliberate: the manifest is the existing model serialised, not a second
model to keep in sync.

Headers the client depends on, and which are therefore part of the contract:

- `Access-Control-Allow-Origin: *` on the catalogue; the configured app origins on the locker.
- `Accept-Ranges: bytes`, and honest `416` behaviour. A 98 MB download on a phone network that
  cannot resume is a download that does not finish.
- `Cache-Control: public, max-age=31536000, immutable` on content-addressed paths, and something
  short on the manifests. Content addressing is what makes that safe.
- No transfer encoding on paks and zips. They are already compressed; gzipping them costs CPU on
  both ends and saves nothing.

Authentication for the locker is a bearer token the player pastes or obtains once, and the app
stores it where it stores its other settings. Not a password prompt, not an OAuth dance in a
first-run flow: the smallest thing that identifies one owner to their own files.

## What changes in the client

`DataSource` grows a third case. It has two today — `FreeDownload` and `UserSupplied` — and the
new one is neither: the file is the player's own, and it arrives over HTTP.

```kotlin
public data class OwnedOrigin(val key: String) : DataSource
```

A gate lists it beside `UserSupplied` and says nothing about a URL, because the URL is a property
of the player's configuration rather than of the gate. `korax`, `chthon`, `eidolon` and `macil`
therefore stop being "user-supplied only" and become "user-supplied, from a picker or your own
origin" — the same policy, one more transport.

`DataDownload` grows ranged, resumable fetches. It is an interface with one method today, which
is the right shape but not enough of it: a resumed download needs to say where it is starting
from, and a caller needs the total to draw progress. That is item 47, and it lands as an
extension of the existing interface rather than a second one, so `FreeDownload` benefits from
resume too.

Settings gains an origin section: base URL, token, a reachability check that says what it found,
and a per-gate list of what the locker is holding. The check matters more than it sounds — a
misconfigured origin should fail in Settings, once, with a readable message, rather than inside a
first-run flow the player is trying to get through.

Upload is the last piece and the one that makes the locker worth having: a gate whose data is
already installed offers to put a copy on the origin, hashed on the way out, so the next device
finds it there. Item 49.

## Per-gate effect

| Gate | Before | After |
|---|---|---|
| `mars` | Freedoom download, broken on web | Catalogue download, working on all three |
| `corvus` | Blasphemer download, broken on web | Catalogue download, working on all three |
| `korax` | File picker only | Picker or the player's locker |
| `chthon` | File picker only, both paks, strict | Picker or locker; strictness unchanged |
| `eidolon` | File picker only, both paks at 1.11, strict | Picker or locker; strictness unchanged |
| `macil` | File picker only, `voices.wad` optional | Picker or locker |

Add-ons — custom map packs, Deathkings, Portal of Praevus — follow the gate they belong to: they
are locker content, never catalogue content, because none of them is freely licensed.

## Delivery plan

Items continue the numbering, per §F. One pull request each, micro commits inside. None of these
depend on phase 7 or phase 8; items 46, 47 and 50 are worth doing before either, because they fix
a limitation the shipped app has today.

| Item | Scope | Depends on |
|---|---|---|
| 46 | `feat(host-gamedata)`: manifest client, `DataSource.OwnedOrigin`, sha256 verification | — |
| 47 | `feat(host-gamedata)`: ranged, resumable downloads across all three platforms | 46 |
| 48 | `feat(launcher)`: origin settings — base URL, token, reachability, per-gate contents | 46 |
| 49 | `feat(host-gamedata)`: upload an installed file to the player's locker | 48 |
| 50 | `feat(games-mars, games-corvus)`: the free replacements come from the catalogue | 46 |
| 51 | `ci`: catalogue manifest verified against its own hashes; no-assets gate still passes | 46 |
| 52 | `docs`: the README's data section describes a path that works | 50 |

The `slipgate-server` repository carries its own plan: object layout, manifest generation, the
locker's auth and quota, deployment, and the licence audit that gates anything entering the
catalogue. What binds the two together is the manifest schema in this document — versioned at
`/catalogue/v1.json`, so a change to it is visible as a new path rather than as a client that
mysteriously stops parsing.

## Open questions

- **Bandwidth.** A 98 MB locker download per device per game is the kind of number that decides
  where this is hosted. Worth a cost estimate before item 49 makes uploads easy.
- **Is the locker better than persistence?** The web target already keeps installed data in OPFS,
  and Android and iOS keep it on disk. The locker's value is a *new* device, not a returning one,
  which is a narrower case than it first appears. If item 48 lands and nobody configures an
  origin, that is the answer.
- **One token or per-device tokens?** Per-device is revocable and is what a lost phone wants; one
  token is what a player will actually paste. Decide in item 48, in the pull request, with the
  reason written down.
