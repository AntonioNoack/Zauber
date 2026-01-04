package me.anno.zauber.generator.java

import me.anno.zauber.generator.Generator
import me.anno.zauber.types.Scope
import java.io.File

// todo before generating JVM bytecode, create source code to be compiled with a normal Java compiler
//  big difference: stack-based
object JavaSourceGenerator: Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}