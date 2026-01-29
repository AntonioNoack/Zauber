package me.anno.zauber.tokenizer

abstract class Tokenizer(val src: String, fileName: String) {

    var i = 0
    var n = src.length
    val tokens = TokenList(src, fileName)
    var tokenizeComments = false

    fun skipLineComment() {
        val i0 = i
        check(src.startsWith("//", i))
        i += 2
        while (i < n && src[i] != '\n') i++
        if (tokenizeComments) {
            tokens.add(TokenType.LINE_COMMENT, i0, i - 1)
        }
    }

    fun skipBlockComment() {
        val i0 = i
        check(src.startsWith("/*", i))
        i += 2
        var depth = 1
        while (depth > 0 && i + 1 < n) {
            if (src[i] == '*' && src[i + 1] == '/') depth--
            else if (src[i] == '/' && src[i + 1] == '*') depth++
            i++
        }
        i++ // skip last symbol
        if (tokenizeComments) {
            tokens.add(TokenType.BLOCK_COMMENT, i0, i - 1)
        }
    }

    abstract fun tokenize(): TokenList

}