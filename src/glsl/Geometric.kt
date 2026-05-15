@file:Suppress("unused")

package glsl

import kotlin.math.hypot
import kotlin.math.sqrt

fun dot(a: vec2, b: vec2): Float =
    a.x * b.x + a.y * b.y

fun dot(a: vec3, b: vec3): Float =
    a.x * b.x + a.y * b.y + a.z * b.z

fun dot(a: vec4, b: vec4): Float =
    a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w

fun cross(a: vec3, b: vec3): vec3 = vec3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x
)

fun length(v: vec2): Float = hypot(v.x, v.y)
fun length(v: vec3): Float = sqrt(dot(v, v))
fun length(v: vec4): Float = sqrt(dot(v, v))

fun normalize(v: vec2) = v / length(v)
fun normalize(v: vec3) = v / length(v)
fun normalize(v: vec4) = v / length(v)