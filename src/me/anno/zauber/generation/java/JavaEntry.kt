package me.anno.zauber.generation.java

data class JavaEntry(val packagePath: String,) {
    val content = StringBuilder()
    val imports = HashSet<String>()
}