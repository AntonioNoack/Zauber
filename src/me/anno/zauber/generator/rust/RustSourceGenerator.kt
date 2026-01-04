package me.anno.zauber.generator.rust

import me.anno.zauber.generator.Generator
import me.anno.zauber.types.Scope
import java.io.File

// todo this has the same complexity as C, plus we must define ownership somehow...
//  wrapping everything into GC is kind of lame...
object RustSourceGenerator : Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}