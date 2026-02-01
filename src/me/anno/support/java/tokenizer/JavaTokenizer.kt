package me.anno.support.java.tokenizer

import me.anno.zauber.tokenizer.ZauberTokenizerBase

class JavaTokenizer(src: String, fileName: String) :
    ZauberTokenizerBase(src, fileName, KEYWORDS) {

    init {
        supportsDollarInName = true
    }

    companion object {
        private val KEYWORDS = setOf(
            "true", "false", "null",
            "package", "import",
            "class", "interface",
            "abstract", "override", "final", "sealed",
            "public", "private", "protected",

            "if", "else", "do", "while", "switch", "for",
            "break", "continue",
            "return", "throw",
            "instanceof",

            "super", "this", "new",
            "try", "catch", "finally", "synchronized",
            "static", "volatile", "assert", "record"
        )
    }
}