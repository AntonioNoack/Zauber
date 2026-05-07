package me.anno.generation.java

import me.anno.generation.DeltaWriter
import me.anno.generation.Generator
import java.io.File

class JavaWriter(root: File) : DeltaWriter<JavaEntry>(root) {
    companion object {
        fun StringBuilder.appendPath(path: List<String>) {
            for (i in path.indices) {
                if (i > 0) append('.')
                append(path[i])
            }
        }

        fun StringBuilder.appendImports(imports: Map<String, List<String>>) {
            var hadImport = false
            for (import in imports.values) {
                if (import.isNotEmpty()) {
                    append("import ")
                    appendPath(import)
                    append(";\n")
                    hadImport = true
                }
            }
            if (hadImport) append('\n')
        }
    }

    private val builder = Generator.builder

    override fun finishContent(v: JavaEntry): String {
        check(v.packagePath != "?")
        builder.append("package ").append(v.packagePath).append(";\n\n")

        builder.appendImports(v.imports)

        builder.append(v.content)
        return JavaSourceGenerator.finish()
    }
}