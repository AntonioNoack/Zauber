package me.anno.generation.rust

import me.anno.generation.Generator
import me.anno.zauber.scope.Scope
import java.io.File

// todo this has the same complexity as C, plus we must define ownership somehow...
//  wrapping everything into GC is kind of lame...
object RustSourceGenerator : Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}