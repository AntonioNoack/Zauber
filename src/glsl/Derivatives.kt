@file:Suppress("unused", "SpellCheckingInspection")

package glsl

external fun dFdx(v: Float): Float
external fun dFdx(v: vec2): vec2
external fun dFdx(v: vec3): vec3
external fun dFdx(v: vec4): vec4
external fun dFdy(v: Float): Float
external fun dFdy(v: vec2): vec2
external fun dFdy(v: vec3): vec3
external fun dFdy(v: vec4): vec4
external fun fwidth(v: Float): Float
external fun fwidth(v: vec2): vec2
external fun fwidth(v: vec3): vec3
external fun fwidth(v: vec4): vec4