package me.anno.generation

import me.anno.generation.java.JavaSourceGenerator

class FileEntry(val packagePath: List<String>, generator: JavaSourceGenerator) {

    val content = StringBuilder(generator.builder)
    val imports = HashMap<String, List<String>>(generator.imports)
    val nativeImports = LinkedHashSet<String>(generator.nativeImports)

    init {
        generator.imports.clear()
        generator.nativeImports.clear()
        generator.builder.clear()
    }
}