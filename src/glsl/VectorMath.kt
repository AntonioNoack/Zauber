@file:Suppress("unused")

package glsl

// todo this is very incomplete :(,
//  can we possibly support this in Zauber, too???
//  we can apply a function component-wise, iff all components have the same type and fit

fun sin(v: vec2): vec2 = vec2(sin(v.x), sin(v.y))
fun sin(v: vec3): vec3 = vec3(sin(v.x), sin(v.y), sin(v.z))
fun sin(v: vec4): vec4 = vec4(sin(v.x), sin(v.y), sin(v.z), sin(v.w))