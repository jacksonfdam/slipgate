# The Slipgate site

Two things share one Vercel project, deployed with the root directory set to `site`:

- **The page** — `index.html` and `assets/`: what Slipgate is, the gates and their lore, the
  data shelf, the engines and the credits. This is what `https://slipgate.vercel.app/` shows.
- **The beacon** — `api/beacon/[id].js`, reached at `/beacon/<id>`: the small document a
  tunnelled data shelf publishes itself to, so every device is configured with one address
  that never changes. It has no page and no link from the page — the beacon id is a read
  credential, and hiding the surface is part of keeping it one. The full story is
  [docs/data-shelf.md](../docs/data-shelf.md).

## Deploy

```bash
cd site
vercel link
vercel blob create-store slipgate-beacon
vercel env add SLIPGATE_BEACON_TOKEN production
vercel deploy --prod
```

The Blob store holds one small text document per beacon and is provisioned with its own token
by Vercel. `SLIPGATE_BEACON_TOKEN` is yours to choose — generate it with
`openssl rand -hex 24` — and it is what the NAS proves itself with when it publishes.

## What is here

| File | What it is |
|---|---|
| `index.html` | The page, self-contained: the app's own palette, type scale and wordmark, ported from `ui/` |
| `assets/` | The gate covers and the favicon, the same art the app ships |
| `api/beacon/[id].js` | The beacon. `POST` to publish with the token, `GET` to read with the id, `DELETE` to forget |
| `vercel.json` | Serves the beacon at `/beacon/<id>` as well, and keeps every answer uncached |

## Why not a database

One document per beacon, a few hundred bytes, written when a NAS reboots and read when an app
opens. Blob is the smallest thing that stores it durably with immediate reads; Edge Config
would be the other candidate and takes seconds to propagate a write, which is exactly the
wrong trade for a pointer that has just changed.
