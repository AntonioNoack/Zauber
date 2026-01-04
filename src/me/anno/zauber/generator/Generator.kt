package me.anno.zauber.generator

import me.anno.zauber.types.Scope
import java.io.File

// todo we could compile to other languages, too,
//  to make interop easier, e.g. if we wanted to write something in PyTorch, just with Kotlin/Zauber as the style,
//  -> implement a JavaScript and a Python backend, too :3 -> surely they will share lots of logic

abstract class Generator(val blockSuffix: String = "}\n") {

    val builder = StringBuilder()

    var depth = 0
    fun indent() {
        repeat(depth) { builder.append("  ") }
    }

    fun open() {
        indent()
        depth++
    }

    fun close() {
        depth--
        indent()
    }

    fun block(run: () -> Unit) {
        open()
        run()
        close()
        builder.append(blockSuffix)
    }

    abstract fun generateCode(dst: File, root: Scope)
}