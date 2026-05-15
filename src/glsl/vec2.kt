package glsl

@Suppress("ClassName", "unused")
data class vec2(
    val x: Float,
    val y: Float
) {
    constructor(v: Float) : this(v, v)

    val r get() = x
    val g get() = y

    val s get() = x
    val t get() = y

    operator fun plus(other: vec2) = vec2(x + other.x, y + other.y)
    operator fun minus(other: vec2) = vec2(x - other.x, y - other.y)
    operator fun times(other: vec2) = vec2(x * other.x, y * other.y)
    operator fun div(other: vec2) = vec2(x / other.x, y / other.y)

    operator fun plus(v: Float) = vec2(x + v, y + v)
    operator fun minus(v: Float) = vec2(x - v, y - v)
    operator fun times(v: Float) = vec2(x * v, y * v)
    operator fun div(v: Float) = vec2(x / v, y / v)

    operator fun unaryMinus() = vec2(-x, -y)

    val xy get() = vec2(x, y)
    val yx get() = vec2(y, x)
}