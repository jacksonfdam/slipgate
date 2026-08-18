# Licensing notes

Slipgate combines code under two incompatible-by-design licences. This file records where
the boundary sits and what remains genuinely unresolved about it.

## The two halves

**The host is dual licensed MIT / Apache 2.0.** That covers everything written for this
project: `host/*`, `launcher`, `ui`, `games/*`, the platform entry points, and the build
tooling. Pick whichever of the two licences suits you.

**The engines are GPLv2.** Chocolate Doom, and the Heretic and Hexen code it carries,
are released under the GNU General Public License version 2. Anything built from that
source is GPLv2, which includes:

- the `.wasm` modules produced by `tooling/engine-build`
- the out-of-tree platform layer in `tooling/engine-build/platform`, which is written
  against the engines' internal interfaces and is a derivative work of them
- any native library built from the same sources

The engine sources themselves are never vendored into this repository. They are fetched at
build time from a pinned upstream tag recorded in `tooling/engine-build/SOURCES.lock`.

## What is unsettled

Whether a permissively licensed host that loads a GPLv2 WebAssembly module forms a single
combined work under the GPL is **contested, not settled**. The arguments run roughly:

- The host and the module are separate programs communicating across a narrow, documented
  boundary — exported functions and a linear memory buffer — which resembles the
  arm's-length separation the FSF has historically accepted for separate processes.
- Against that: the module is loaded into the host's address space by the host, cannot run
  without it, and the two are shipped together in one artifact, which resembles linking.

This project does not claim the question is closed. What it does instead:

- keeps the engine-derived code in its own modules and its own licence
- keeps the boundary between host and engine explicit and documented
- distributes the complete corresponding source for every engine module, along with the
  exact upstream revision and the scripts needed to reproduce it

If you redistribute Slipgate with engine modules included, treat the result as GPLv2 and
honour the source-offer obligations. If you only want the host, take it under MIT or
Apache 2.0 and supply your own engines.

## Game data

No game data of any kind belongs in this repository, in its history, in a release artifact,
or in a CI cache. That includes shareware and freeware WADs, and it includes files added
"just for testing". `.gitignore` refuses the common extensions, but the rule is a licensing
rule, not a tooling one.

Freely licensed replacements — Freedoom for Doom, Blasphemer for Heretic — are downloaded by
the user at first run from their own official release pages. Hexen has no free replacement,
so it accepts user-supplied data only.

## Attribution

Slipgate's architecture follows [mood](https://github.com/CharlieTap/mood), which is dual
MIT / Apache 2.0. No source has been copied from it. Should that change, the borrowed code
will be recorded in a `NOTICE` file naming the upstream commit.

The [Chasm](https://github.com/CharlieTap/chasm) WebAssembly runtime is a build dependency
under its own licence, not a derivative work of this project.
