@file:Suppress("unused", "SpellCheckingInspection")

package glsl

import kotlin.math.pow

fun sin(x: Float): Float = kotlin.math.sin(x)
fun cos(x: Float): Float = kotlin.math.cos(x)
fun tan(x: Float): Float = kotlin.math.tan(x)
fun asin(x: Float): Float = kotlin.math.asin(x)
fun acos(x: Float): Float = kotlin.math.acos(x)
fun atan(y: Float, x: Float): Float = kotlin.math.atan2(y, x)
fun pow(x: Float, y: Float): Float = x.pow(y)
fun exp(x: Float): Float = kotlin.math.exp(x)
fun log(x: Float): Float = kotlin.math.ln(x)
fun sqrt(x: Float): Float = kotlin.math.sqrt(x)
fun inversesqrt(x: Float): Float = 1f / kotlin.math.sqrt(x)
fun abs(x: Float): Float = kotlin.math.abs(x)
fun floor(x: Float): Float = kotlin.math.floor(x)
fun ceil(x: Float): Float = kotlin.math.ceil(x)
fun round(x: Float): Float = kotlin.math.round(x)
fun fract(x: Float): Float = x - floor(x)
fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
fun clamp(x: Float, minVal: Float, maxVal: Float): Float =
    max(minVal, min(x, maxVal))

fun mix(a: Float, b: Float, t: Float): Float =
    a + (b - a) * t

fun step(edge: Float, x: Float): Float =
    if (x < edge) 0f else 1f

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
    return t * t * (3f - 2f * t)
}