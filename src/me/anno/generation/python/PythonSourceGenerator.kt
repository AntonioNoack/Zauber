package me.anno.generation.python

import me.anno.generation.Generator
import me.anno.zauber.scope.Scope
import java.io.File

// todo this is just like JavaScript source code,
//  just a little different indentation, and classes look different
object PythonSourceGenerator : Generator("\n") {
    override fun generateCode(dst: File, root: Scope) {
        TODO("Not yet implemented")
    }
}