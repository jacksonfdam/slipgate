// Palette-indexed presentation.
//
// Uniforms:
//   group 0 binding 0  sampler, nearest filtered in both directions
//   group 0 binding 1  indexed frame, r8unorm, one texel per source pixel
//   group 0 binding 2  palette, rgba8unorm, 256 by 1
//
// The palette lookup happens here rather than on the CPU, which is what makes the engines'
// palette effects — damage flash, item pickup tint, the Tome of Power — cost nothing.

const PALETTE_ENTRIES: f32 = 256.0;
const PALETTE_MAX_INDEX: f32 = 255.0;

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
}

@vertex
fn vertexMain(@builtin(vertex_index) vertexIndex: u32) -> VertexOutput {
    // A full-target quad as two triangles' worth of strip vertices. The destination rectangle
    // comes from the render pass viewport, so the geometry never changes.
    let positions = array<vec2f, 4>(
        vec2f(-1.0, -1.0),
        vec2f(1.0, -1.0),
        vec2f(-1.0, 1.0),
        vec2f(1.0, 1.0),
    );
    let uvs = array<vec2f, 4>(
        vec2f(0.0, 1.0),
        vec2f(1.0, 1.0),
        vec2f(0.0, 0.0),
        vec2f(1.0, 0.0),
    );

    var output: VertexOutput;
    output.position = vec4f(positions[vertexIndex], 0.0, 1.0);
    output.uv = uvs[vertexIndex];
    return output;
}

@group(0) @binding(0) var frameSampler: sampler;
@group(0) @binding(1) var indexedTexture: texture_2d<f32>;
@group(0) @binding(2) var paletteTexture: texture_2d<f32>;

@fragment
fn fragmentMain(input: VertexOutput) -> @location(0) vec4f {
    let index = textureSample(indexedTexture, frameSampler, input.uv).r;
    // r8unorm hands back 0..1; recover the entry and sample its texel centre.
    let paletteU = (index * PALETTE_MAX_INDEX + 0.5) / PALETTE_ENTRIES;
    return textureSample(paletteTexture, frameSampler, vec2f(paletteU, 0.5));
}
