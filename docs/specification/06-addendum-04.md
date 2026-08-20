# Addendum 04 — the Quake gate

Amends [02-addendum-01.md](02-addendum-01.md) §C, §F phase 7 and §G. Everything else stands,
including every non-negotiable rule.

## The decision gate is passed

Phase 7 said the Quake gate had to be confirmed as still wanted before its costs were paid. It
was, by the owner, on 2026-08-19. The engine choice was re-examined at the same time: **Mark V
WinQuake** stands, the same lineage QuakeDroid proved on mobile. jake2 was considered and
rejected — it is Quake II, not Quake, it targets the desktop JVM through OpenGL, and a renderer
that bypasses the palette-indexed pipeline has no place under this host. It stays noted as a
possible future gate of its own, nothing more.

## Amendments to phase 7

**Game data is strict.** Item 36 read "user-supplied `pak0.pak` and `pak1.pak` from `id1` only".
That now means both files, both required: the registered game from a copy the player owns.
Shareware Quake — `pak0.pak` alone — is recognised, so the rejection can say exactly what is
missing and why, but it does not boot. Nothing is ever downloaded or bundled.

**The web is in scope, and costs nothing new.** Item 36 assumed the browser path needed its own
driver; that driver has since shipped, and the Doom gate already runs on it. The Quake gate's web
half is the same module built for wasm — `chthon.wasm` through the existing driver — so the gate
lands on all three platforms in its own pull request, not behind new web machinery.

**The sources are pinned through a mirror.** Mark V has no upstream git repository; the source is
an archive on a 2018-era host. A public mirror — `github.com/jacksonfdam/markv-quakedroid`, one
commit per upstream archive, the archive's original URL and sha256 recorded in the mirror — gives
`SOURCES.lock` the same `repository`/`tag`/`commit` pin every other engine has, keeps CI off the
old host, and satisfies the GPL's source-availability obligation in the same move.

**The native build is bash and the NDK's clang, not CMake.** Item 31 named CMake; the build that
shipped mirrors `build-engine.sh` instead — the same pinned sources, the same platform layer, the
same replacement list, read from one shared file. A second build system for a compile-and-link of
one library was the larger change, and §H says to propose the smaller one. ABIs are `arm64-v8a`
and `x86_64` at API 24 with 16 KB pages; artifacts are committed and pinned like the wasm modules,
so CI verifies hashes and never needs the NDK.

**Two engines in one native process collide.** Every engine exports the same `slipgate_*` names —
that is the point — but a wasm world instantiates isolated modules and a native world links one
process. On Android and the JVM the bridge loads each engine with `dlopen` under `RTLD_LOCAL` and
hands the session a handle, so the names never meet. On iOS, where linking is static, a generated
prefix header renames each engine's surface at compile time and cinterop binds the prefixed set.
The closing line of §F — an identical exported surface — is kept as identical *shape*; the prefix
exists only where the linker forces it, and this addendum is where that deviation is recorded.

## Delivery plan

Inserted items use letter suffixes, per §F. One pull request each, micro commits inside.

| Item | Scope | Depends on |
|---|---|---|
| 4b | `refactor(ui)`: backends resolved per platform module | — |
| 31 | `build(tooling)`: dual-target platform layer, native Doom libraries for Android and the build host | — |
| 32 | `feat(backend-native)`: dlopen/JNI bridge, zero-copy framebuffer view, Doom parity proof on the JVM | 4b, 31 |
| 33 | `build(tooling)`: iOS static libraries, prefix header, cinterop definition for the Doom engine | 31 |
| 34 | `feat(backend-native)`: native session on iOS | 32, 33 |
| 35a | `build(tooling)`: the Mark V mirror and its pin in `SOURCES.lock` | — |
| 35b | `build(tooling)`: WinQuake platform layer; `libchthon` for Android, iOS and the host; `chthon.wasm` | 31, 35a |
| 35c | `feat(host-gamedata)`: PAK recognition — inspector, `gfx/palette.lmp` accent, Quake flavour | — |
| 36 | `feat(games-chthon)`: the Quake gate on all three platforms, demo determinism harness, credits | 32, 34, 35b, 35c |
| 37 | `feat(launcher)`: backend override in Settings, `Native` opt-in for the Doom-family gates | 32 |
| 37a | `feat(controls)`: touch look for gates that use the look axis | 36 |

The critical path is 31 → 32 → 35b → 36. Items 35a, 35c and 4b run parallel to it; 33 → 34
runs beside 35a → 35b.

WinQuake specifics that the implementing pull requests hold to: the engine boots as
`Host_Init` plus `Host_Frame` per step — no boot longjmp, that trick is Doom's — and renders
8-bit indexed 320×200 into the existing palette pipeline; the palette arrives from
`gfx/palette.lmp` inside `pak0.pak` and changes often in play, which `SG_PALETTE_CHANGED`
already reports; paks are mounted into an in-memory `id1/` tree behind the engine's
`Sys_File*` seam, and `config.cfg` writes land in scratch memory because Quake writes it on
quit and a gate must not crash on the way out; `id386=0` everywhere, C paths only; determinism
is proven by `playdemo demo1` twice against itself, gated on a `-Pslipgate.pak` property,
exactly as the Doom gate's harness does with its IWAD.

## Attribution

§G already credits Mark V and QuakeDroid where used. The credits screen entry lands with the
gate itself, in item 36, alongside id Software's.
