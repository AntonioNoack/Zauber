package glsl

@Suppress("ClassName")
data class mat4(
    val c0: vec4,
    val c1: vec4,
    val c2: vec4,
    val c3: vec4
) {
    constructor(v: Float) : this(
        vec4(v, 0f, 0f, 0f),
        vec4(0f, v, 0f, 0f),
        vec4(0f, 0f, v, 0f),
        vec4(0f, 0f, 0f, v)
    )

    operator fun times(v: vec4): vec4 {
        return vec4(
            c0.x * v.x + c1.x * v.y + c2.x * v.z + c3.x * v.w,
            c0.y * v.x + c1.y * v.y + c2.y * v.z + c3.y * v.w,
            c0.z * v.x + c1.z * v.y + c2.z * v.z + c3.z * v.w,
            c0.w * v.x + c1.w * v.y + c2.w * v.z + c3.w * v.w
        )
    }
}