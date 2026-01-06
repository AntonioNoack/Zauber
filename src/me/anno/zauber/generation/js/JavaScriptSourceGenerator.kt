package me.anno.zauber.generation.js

import me.anno.zauber.generation.Generator
import me.anno.zauber.types.Scope
import java.io.File

// todo this is just like Java source code, except that
//  a) we don't need to specify what each type is
//  b) we must generate unique method names from their signature, if overloads exist
object JavaScriptSourceGenerator: Generator() {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}