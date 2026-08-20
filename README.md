# Slipgate

A console-style game select screen that boots classic id Tech 1 games. Each engine is
compiled to WebAssembly and driven from a single Kotlin Multiplatform host shell, so the
same launcher, renderer, input layer and save system serve every game behind it.

Slipgate is a launcher first and an engine host second. You pick a gate, the screen warps,
and the game takes over the surface.

Targets: **Android**, **Web (wasmJs)** and **iOS**. All three are first-class — a feature
that works on two of them is not finished.

## Prior art

Slipgate exists because of [mood](https://github.com/CharlieTap/mood) by Charlie Tapping,
which demonstrated that a full id Tech 1 game can be driven from Kotlin Multiplatform via
the [Chasm](https://github.com/CharlieTap/chasm) WebAssembly runtime. The host architecture
here — a palette-indexed framebuffer pipeline fed by a wasm game instance — follows the
approach mood established. Slipgate extends it to multiple engines behind a single
launcher.

No source is copied from mood. The architecture was studied and reimplemented; if that ever
changes, the borrowed code will be attributed in a `NOTICE` file against the upstream
commit it came from.

## Gates

A gate is one game plus the engine that runs it.

| Gate | Engine | Module | Freely licensed data |
|---|---|---|---|
| `mars` | Doom | `:games:mars` | Yes — Freedoom |
| `corvus` | Heretic | `:games:corvus` | Yes — Blasphemer |
| `korax` | Hexen | `:games:korax` | No — user-supplied IWAD only |

All three modules are built from that tree by `tooling/engine-build`, and all three ship with their
gate. Doom and Heretic have been run against real data; Hexen has not, because there is no freely
licensed IWAD to run it against.

All three come from the [Chocolate Doom](https://github.com/chocolate-doom/chocolate-doom)
tree, which carries Doom, Heretic and Hexen behind one platform abstraction. That shared
`i_*` layer is the reason a single port effort yields three gates.

## Architecture

```
host/runtime      gate contract, wasm instance driver, session lifecycle
host/controls     virtual gamepad, keyboard mapping, input profiles
host/graphics     backend contract, WebGPU / Skia / classic backends, upscalers, effects
host/backend/*    execution backends that implement the runtime's contracts
launcher          gate registry, select screen, navigation, in-game overlay
ui                shared Compose shell and theme
games/*           one module per gate
android, web, ios platform entry points
tooling/*         engine build scripts and CI helpers
```

Two dependency rules hold the design together:

- `host/*` must never depend on `games/*`. Gates are discovered through a registry that the
  platform entry point populates.
- `host/runtime` must never depend on `host/backend/*`. The runtime defines contracts,
  backends implement them, and the entry point wires them together.

Nothing in the host assumes 320×200, 8-bit indexed colour, 35 Hz tics or a single save
blob. Those are properties of a session, which is what keeps a future non-Doom engine
viable.

### Graphics backends

| Backend | Platforms | Shader language |
|---|---|---|
| Skia runtime effects | iOS and web through Skiko, Android 33+ through `RuntimeShader` | SkSL / AGSL |
| Classic | Android below 33, and anywhere a runtime effect fails | none |

WebGPU was implemented and then dropped: Compose for web clears its canvas to opaque white and so
cannot draw over a WebGPU canvas, and Jetpack WebGPU only accepts a raw `ANativeWindow` pointer,
which would drag the NDK into a phase that does not need it. One shader dialect drawn inside
Compose's own canvas replaces it — the reasoning is in
[docs/specification/03-addendum-02.md](docs/specification/03-addendum-02.md).

The framebuffer is uploaded as an R8 texture with the palette as a 256-entry lookup
texture, and colour is resolved in the fragment shader. Palette effects — damage flash,
item pickup tint, the Tome of Power — are then free.

## Game data

**No game data ships with this project.** Not in the repository, not in releases, not in CI
caches. Acquisition is a first-run flow inside the app:

- Doom offers a Freedoom download, or accepts a user-supplied IWAD.
- Heretic offers Blasphemer, or accepts a user-supplied IWAD.
- Hexen is user-supplied only, and its gate card says so rather than showing a download
  button that cannot work.

Both free options are named as what they are: a replacement rather than the original. Freedoom
is not Doom and Blasphemer is not Heretic — different levels and art, the same game to play —
and the data screen says so before a player downloads half a gigabyte expecting otherwise.

Supplied files are validated by IWAD header and lump signature, never by filename. A file that
is game data for the wrong game is refused by name: "that is Doom data and this gate needs
Hexen".

**The web cannot download either replacement.** GitHub's release assets send no
`Access-Control-Allow-Origin`, so a browser refuses the request before it starts; the app says
so plainly and the player supplies their own file instead. Serving the data from an origin that
allows it would fix this, and that is a hosting decision rather than a code one.

## Licensing

The Slipgate host is dual licensed under [MIT](LICENSE-MIT) and
[Apache 2.0](LICENSE-APACHE). The engines are GPLv2, so the `.wasm` modules built from them
are GPLv2. See [LICENSE-NOTES.md](LICENSE-NOTES.md) for the boundary between the two and
for an honest account of what is unsettled about it.

## iOS distribution

**Slipgate will not be submitted to the App Store.** Every engine here is GPLv2, which has
never been cleanly compatible with App Store distribution. Apple pulled a GPLv2 GNU Go port
in 2010 after a complaint from the Free Software Foundation; the conflict is GPLv2's
prohibition on imposing further restrictions against Apple's minimum EULA, which grants a
non-transferable licence limited to Apple-branded devices the user owns.

Only copyright holders can act on that, and plenty of GPL apps sit on the store
unchallenged. But Chocolate Doom and the Raven engines carry decades of contributors, and a
post-launch takedown is worse than never submitting. iOS distribution is therefore
build-from-source, sideload, TestFlight, or an alternative EU marketplace under the DMA.

## Building

Requires JDK 21 and, per target, the Android SDK (`compileSdk` 37) or Xcode.

```
./gradlew ktlintCheck detekt                    # static analysis, zero findings tolerated
./gradlew :android:assembleRelease              # unsigned APK
./gradlew :web:wasmJsBrowserDistribution        # web distribution
./gradlew :ui:compileKotlinIosSimulatorArm64    # iOS compilation
./gradlew allTests                              # unit tests, macOS host for the iOS targets
```

The web distribution lands in `web/build/dist/wasmJs/productionExecutable` and can be
served with any static file server.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. The commit and pull request rules are
enforced by CI, not by convention.

The full build specification lives in [docs/specification](docs/specification).
