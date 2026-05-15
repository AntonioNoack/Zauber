package glsl

@Suppress("ClassName", "unused")
data class vec3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    // todo there are too many constructors, too, can we support that in Zauber in some way, too?
    constructor(v: Float) : this(v, v, v)
    constructor(v: vec2, z: Float) : this(v.x, v.y, z)

    val r get() = x
    val g get() = y
    val b get() = z

    // todo we need to support swizzling somehow, ideally in Zauber itself...
    //  listing all functions is too many combinations
    val xy get() = vec2(x, y)
    val yz get() = vec2(y, z)
    val xyz get() = vec3(x, y, z)
    val yzx get() = vec3(y, z, x)
    val zyx get() = vec3(z, y, x)

    operator fun plus(other: vec3) = vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: vec3) = vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(other: vec3) = vec3(x * other.x, y * other.y, z * other.z)
    operator fun div(other: vec3) = vec3(x / other.x, y / other.y, z / other.z)

    operator fun plus(v: Float) = vec3(x + v, y + v, z + v)
    operator fun minus(v: Float) = vec3(x - v, y - v, z - v)
    operator fun times(v: Float) = vec3(x * v, y * v, z * v)
    operator fun div(v: Float) = vec3(x / v, y / v, z / v)
}