@file:Suppress("ClassName", "unused")

package glsl

class sampler2D
class samplerCube

external fun texture(
    sampler: sampler2D,
    coord: vec2
): vec4

external fun textureLod(
    sampler: sampler2D,
    coord: vec2,
    lod: Float
): vec4

external fun texelFetch(
    sampler: sampler2D,
    coord: ivec2,
    lod: Int
): vec4