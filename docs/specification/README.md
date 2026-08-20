# Build specification

The repository is the single source of truth for what Slipgate is meant to be. This
directory holds the specification it is built against, in the order it was written.

| Document | Contents |
|---|---|
| [01-foundation.md](01-foundation.md) | Project definition, module layout, gate contract, engine-to-wasm pipeline, graphics, CI gates, delivery plan |
| [02-addendum-01.md](02-addendum-01.md) | iOS as a first-class target, the Skia backend, the backend axis, additional labels and CI gates, plan deltas |
| [03-addendum-02.md](03-addendum-02.md) | The shader path: why WebGPU is out and one SkSL/AGSL pipeline inside Compose is in |
| [04-addendum-03.md](04-addendum-03.md) | Custom maps: role read from the palette, add-ons on the shelf, and the launch options queued behind them |
| [05-strife-gate.md](05-strife-gate.md) | Working notes for a fourth gate: what Strife needs that the other three did not, and its art |
| [06-addendum-04.md](06-addendum-04.md) | The Quake gate: phase 7 confirmed, its amendments, and the delivery plan |
| [07-addendum-05.md](07-addendum-05.md) | The Hexen II gate: uHexen2, what phase 8 inherits from phase 7, and the delivery plan |
| [08-addendum-06.md](08-addendum-06.md) | The data origin: what `slipgate-server` may serve, what it may not, and what changes in the client |

Later documents amend earlier ones rather than replacing them. Where an amendment conflicts
with something already merged, the pull request that resolves it says so explicitly and
proposes the smaller change; working code is not rewritten to match a document.

Rules that apply to every change — English only, micro commits, Conventional Commits, one
pull request per feature, rebase and merge, latest stable dependency versions, no game data
in the repository — are restated in [CONTRIBUTING.md](../../CONTRIBUTING.md), which is what
CI enforces.
