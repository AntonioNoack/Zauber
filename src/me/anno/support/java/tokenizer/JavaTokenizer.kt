package me.anno.support.java.tokenizer

import me.anno.zauber.tokenizer.ZauberTokenizerBase

class JavaTokenizer(src: String, fileName: String) :
    ZauberTokenizerBase(src, fileName, KEYWORDS) {

    companion object {
        private val KEYWORDS = setOf(
            "true", "false", "null",
            "package", "import",
            "class", "interface",
            "abstract", "override",

            "if", "else", "do", "while", "switch", "for",
            "break", "continue",
            "return", "throw",
            "instanceof",

            "super", "this",
            "try", "catch", "finally"
        )
    }
}