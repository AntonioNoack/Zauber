package me.anno.generation.java

import me.anno.generation.DeltaWriter
import java.io.File

class JavaWriter(root: File) : me.anno.generation.DeltaWriter<me.anno.generation.java.JavaEntry>(root) {
    val builder = _root_ide_package_.me.anno.generation.java.JavaSourceGenerator.builder

    override fun finishContent(v: me.anno.generation.java.JavaEntry): String {
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