# Addendum 02 — the shader path

Amends [02-addendum-01.md](02-addendum-01.md) §B and the graphics half of
[01-foundation.md](01-foundation.md) §6. Everything else stands.

This addendum exists because two facts were measured while implementing the WebGPU pipeline, and
both of them contradict the backend table addendum 01 set out.

## What was measured

**Compose for web clears its canvas to opaque white.** Sampling the Compose canvas returns
`255,255,255,255` wherever the composition draws nothing. It therefore cannot composite over a
canvas beneath it. A WebGPU canvas can only be seen on web by stacking it *above* Compose, which
hides every pixel the shell draws — acceptable for a debug label, fatal for the in-game overlay
menu of plan item 20.

**Jetpack WebGPU cannot take an Android `Surface`.** In `androidx.webgpu:1.0.0-alpha05` the only
surface source is `GPUSurfaceSourceAndroidNativeWindow(long window)`, which wants a raw
`ANativeWindow` pointer. Obtaining one from a `Surface` requires a JNI shim, so an Android WebGPU
driver drags the NDK and CMake pipeline into phase 2 — the cost addendum 01 §F deliberately
reserved for phase 7 — and ships four ABIs of Dawn in the APK.

## The revised backend table

| Backend | Platforms | Shader language |
|---|---|---|
| Skia runtime effects | iOS and web through Skiko, Android 33+ through `RuntimeShader` | SkSL / AGSL |
| Classic | Android below 33, and anywhere a runtime effect fails | none |

WebGPU is out. AGSL is SkSL with restrictions, so one shader source serves every platform that
has a shader path at all, and every one of them draws **inside Compose's own canvas**. There is no
second surface to composite, no interop layer, and no pre-release dependency.

Consequences, stated rather than discovered later:

- Android below API 33 has no shader path. The classic backend is not a placeholder there; it is
  the answer. `minSdk` stays 24 because the engines will run on those devices perfectly well
  without a CRT filter.
- The cross-backend golden image test of item `7b` becomes stronger, not weaker: it now compares
  paths that share a shader source, so a difference is a bug rather than a dialect.
- The `host/graphics/backend/webgpu` module is deleted rather than kept behind a flag. Unused code
  guarded by configuration is the thing this project's working method says to avoid. The branch
  `feat/graphics-webgpu-palette-pipeline` remains on the remote as the record of a working WebGPU
  path, including its WGSL shader.
- Shader sources keep the `shaders/` directory and the build-time embedding written for WebGPU.
  Sibling files per dialect stay the rule; there is simply one dialect now.

## Plan deltas

- Item 7 (`feat(graphics-webgpu)`) is **replaced** by `7s` `feat(graphics-skia)`: the SkSL and AGSL
  palette pipeline across iOS, web and Android 33+, with automatic fallback to classic.
- Item `7a` is absorbed into `7s`; there is no separate iOS shader item, because iOS is no longer
  a separate dialect.
- Item `7b` (cross-backend golden image) stands, and compares Skia against classic.
- Item 8 (CRT) is now one shader source rather than two.
- Phase 7's native backend work is unaffected: it was always about running Quake's software
  renderer, not about presenting frames.
