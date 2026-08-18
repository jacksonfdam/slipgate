# Contributing

These rules are enforced by CI. A pull request that breaks one of them fails rather than
receiving a comment about it.

## Language

English only, everywhere: code, comments, commit messages, branch names, pull request
titles and bodies, issue text, documentation and user-facing strings.

## Commits

Micro commits. One logical change per commit. If the subject needs the word "and", the
commit needs splitting. Aim under roughly 150 changed lines, excluding generated files and
lock files.

[Conventional Commits](https://www.conventionalcommits.org), strictly:

```
type(scope): subject
```

- **type** is one of `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`,
  `chore`.
- **scope** is optional and, when present, is the module short name: `host-runtime`,
  `graphics-webgpu`, `graphics-skia`, `launcher`, `controls`, `audio`, `games-mars`,
  `games-corvus`, `games-korax`, `ui`, `web`, `android`, `ios`, `tooling`.
- **subject** is imperative mood, starts lowercase, has no trailing period, and stays
  within 72 characters.

Commits carry no attribution trailers or generation footers of any kind. Every commit reads
as the work of the person who authored it. `tooling/ci/scan-attribution.sh` checks commit
messages, changed files and the pull request body for such text.

Verify locally before pushing:

```
./tooling/ci/lint-commit-messages.sh main HEAD
./tooling/ci/scan-attribution.sh main HEAD
```

## Branches

```
<type>/<scope>-<short-description>
```

For example `feat/host-runtime-gate-contract` or `build/tooling-chocolate-doom-fetch`.

Never commit to `main`. The branch is protected: pull requests are required, force pushes
and deletions are rejected, and the protection applies to administrators too.

## Pull requests

One pull request per feature, and one feature per pull request.

The title follows the same Conventional Commits format as a commit, using the dominant
change type. The body contains:

- **What** — two to four sentences.
- **Why** — the reason this change exists now.
- **How to verify** — exact commands and their expected result.
- **Platform coverage** — Android, Web and iOS, each marked done or given an explicit
  reason for not being covered.

Anything that changes what a user sees needs a screenshot or a short capture.

Labels: exactly one `type:` label, chosen deliberately, and at least one `area:` label.
Path-based automation may add `area:`, `platform:` and `gate:` labels; it never sets the
`type:` label.

Pull requests merge with **rebase and merge** so the micro commit history survives. Squash
merging is disabled at the repository level.

## Code

- Idiomatic Kotlin, DRY, SOLID. Explicit API mode is on for library modules.
- No `!!`.
- No platform-specific type may leak through a `commonMain` interface.
- Koin for dependency injection. Module definitions live next to the feature they wire and
  are aggregated once per platform entry point.
- Every `expect` declaration lands with **all three** actuals — Android, iOS and wasmJs — in
  the same pull request. An `expect` merged without an actual is debt that blocks the next
  feature pull request in that module.
- Dark mode is the default and must be correct. Light mode must remain legible.
- Comments explain why, not what.
- Prefer deleting code over adding a configuration flag for it.

## Dependencies

Only the latest stable version, and only after checking what the latest stable version
actually is at the moment you add it. Never copy a version number from memory or from an
example. All versions live in `gradle/libs.versions.toml`.

## Game data

Never commit game data. See [LICENSE-NOTES.md](LICENSE-NOTES.md).

## Shaders

Shader sources live in resource files, one per dialect, with matching structure and
comments. Shader strings are never written inline in Kotlin. A cross-backend golden image
test is what keeps the WGSL and SkSL paths from drifting apart.
