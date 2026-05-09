package me.anno.generation

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.expansion.DependencyData
import java.io.File

// todo we could compile to other languages, too,
//  to make interop easier, e.g. if we wanted to write something in PyTorch, just with Kotlin/Zauber as the style,
//  -> implement a JavaScript and a Python backend, too :3 -> surely they will share lots of logic

abstract class Generator(val blockSuffix: String = "}\n") {
    companion object {
        val builder = StringBuilder()

        var depth = 0
        fun indent() {
            repeat(depth) { builder.append("  ") }
        }

        var commentDepth = 0
        fun comment(body: () -> Unit) {
            commentDepth++
            try {
                builder.append(if (commentDepth == 1) "/* " else "(")
                body()
                builder.append(if (commentDepth == 1) " */" else ")")
            } finally {
                commentDepth--
            }
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

        fun writeBlock(run: () -> Unit) {
            if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
            builder.append("{")

            depth++
            nextLine()

            try {
                run()

                if (builder.endsWith("  ")) {
                    builder.setLength(builder.length - 2)
                }

                depth--
                builder.append("}\n")
                indent()
                depth++

            } finally {
                depth--
            }
        }

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

    open fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        throw NotImplementedError("${javaClass.simpleName}.generateCode()")
    }
}