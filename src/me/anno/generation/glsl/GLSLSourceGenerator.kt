package me.anno.generation.glsl

import me.anno.generation.Generator
import me.anno.zauber.scope.Scope
import java.io.File

// todo given a main function and some context info,
//  collect all necessary dependencies,
//  and generate compilable GLSL from it, including uniform buffers and bindings to get the necessary context data
// todo big difference to C/Java: allocations are only possible on the stack, pointers are limited to out-variables
object GLSLSourceGenerator: me.anno.generation.Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}