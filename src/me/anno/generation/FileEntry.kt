package me.anno.generation

import me.anno.generation.java.Import2
import me.anno.generation.java.JavaSourceGenerator

class FileEntry(val packagePath: List<String>, generator: JavaSourceGenerator) {

    val content = StringBuilder(generator.builder)
    val imports = HashMap<String, Import2>(generator.imports)
    val nativeImports = LinkedHashSet<String>(generator.nativeImports)

    init {
        generator.imports.clear()
        generator.nativeImports.clear()
        generator.builder.clear()
    }
}