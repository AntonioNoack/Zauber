package glsl

@Suppress("ClassName", "unused")
data class vec4(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
) {
    constructor(v: Float) : this(v, v, v, v)
    constructor(v: vec3, w: Float) : this(v.x, v.y, v.z, w)

    val r get() = x
    val g get() = y
    val b get() = z
    val a get() = w

    val xyz get() = vec3(x, y, z)
    val rgb get() = vec3(r, g, b)

    operator fun plus(other: vec4) = vec4(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun minus(other: vec4) = vec4(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun times(other: vec4) = vec4(x * other.x, y * other.y, z * other.z, w * other.w)
    operator fun div(other: vec4) = vec4(x / other.x, y / other.y, z / other.z, w / other.w)

    operator fun plus(other: Float) = vec4(x + other, y + other, z + other, w + other)
    operator fun minus(other: Float) = vec4(x - other, y - other, z - other, w - other)
    operator fun times(other: Float) = vec4(x * other, y * other, z * other, w * other)
    operator fun div(other: Float) = vec4(x / other, y / other, z / other, w / other)
}