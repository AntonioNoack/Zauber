package me.anno.generation.java

import me.anno.generation.DeltaWriter
import java.io.File

class JavaWriter(root: File) : DeltaWriter<JavaEntry>(root) {
    val builder = JavaSourceGenerator.builder

    override fun finishContent(v: JavaEntry): String {
        check(v.packagePath != "?")
        builder.append("package ").append(v.packagePath).append(";\n\n")
        if (v.imports.isNotEmpty()) {
            for (import in v.imports) {
                builder.append("import ").append(import).append(";\n")
            }
            builder.append('\n')
        }
        builder.append(v.content)
        return JavaSourceGenerator.finish()
    }
}