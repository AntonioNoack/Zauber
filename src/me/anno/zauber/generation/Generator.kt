package me.anno.zauber.generation

import me.anno.zauber.generation.java.JavaSimplifiedASTWriter
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

    var commentDepth = 0
    fun comment(body: () -> Unit) {
        commentDepth++
        builder.append(if (commentDepth == 1) "/* " else "(")
        body()
        builder.append(if (commentDepth == 1) " */" else ")")
        commentDepth--
    }

    fun trimWhitespaceAtEnd() {
        var lastIndex = builder.lastIndex
        while (lastIndex >= 0 && builder[lastIndex].isWhitespace()) {
            lastIndex--
        }
        builder.setLength(lastIndex + 1)
    }

    fun openBlock() {
        indent()
        depth++
    }

    fun closeBlock() {
        depth--
        indent()
    }

    fun nextLine() {
        builder.append('\n')
        indent()
    }

    fun block(run: () -> Unit) {
        openBlock()
        run()
        closeBlock()
        builder.append(blockSuffix)
    }

    fun finish(): String {
        val str = builder.toString()
        builder.clear()
        return str
    }

    abstract fun generateCode(dst: File, root: Scope)
}