package me.anno.support.javascript

import me.anno.support.javascript.ast.TypeScriptClassScanner
import me.anno.support.javascript.tokenizer.TypeScriptTokenizer
import java.io.File

object StandardLibraryLoader {
    fun loadStandardLibrary(file: File) {
        val tokens = TypeScriptTokenizer(file.readText(), file.absolutePath).tokenize()
        TypeScriptClassScanner(tokens).readFileLevel()
    }
}