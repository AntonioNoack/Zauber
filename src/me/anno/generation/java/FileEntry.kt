package me.anno.generation.java

data class FileEntry(val packagePath: String) {
    val content = StringBuilder()
    val imports = HashMap<String, List<String>>()
}