package me.anno.zauber.generator.glsl

import me.anno.zauber.generator.Generator
import me.anno.zauber.types.Scope
import java.io.File

// todo given a main function and some context info,
//  collect all necessary dependencies,
//  and generate compilable GLSL from it, including uniform buffers and bindings to get the necessary context data
// todo big difference to C/Java: allocations are only possible on the stack, pointers are limited to out-variables
object GLSLSourceGenerator: Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}