# The Slipgate site

A page explaining the home library, and the beacon a home library publishes itself to. Deployed to
Vercel as its own project with the root directory set to `site`. The full setup guide is
[docs/home-library.md](../docs/home-library.md).

## Deploy

```bash
cd site
vercel link
vercel blob store add slipgate-beacon
vercel env add SLIPGATE_BEACON_TOKEN production
vercel deploy --prod
```

The Blob store holds one small text document per beacon and is provisioned with its own token by
Vercel. `SLIPGATE_BEACON_TOKEN` is yours to choose — generate it with `openssl rand -hex 24` — and
it is what the NAS proves itself with when it publishes.

## What is here

| File | What it is |
|---|---|
| `index.html` | The page: how the library works, how to boot it, and a checker that reports what a beacon points at |
| `api/beacon/[id].js` | The beacon. `POST` to publish with the token, `GET` to read with the id, `DELETE` to forget |
| `vercel.json` | Serves the beacon at `/beacon/<id>` as well, and keeps every answer uncached |

## Why not a database

One document per beacon, a few hundred bytes, written when a NAS reboots and read when an app opens.
Blob is the smallest thing that stores it durably with immediate reads; Edge Config would be the
other candidate and takes seconds to propagate a write, which is exactly the wrong trade for a
pointer that has just changed.
