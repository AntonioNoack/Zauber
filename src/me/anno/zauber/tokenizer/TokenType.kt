package me.anno.zauber.tokenizer

enum class TokenType {

    NAME, // starts with A-Za-z_
    STRING,
    NUMBER, // starts with 0-9.; a char is a special number
    SYMBOL, // anything like +-*/=&%$§
    LABEL,

    KEYWORD,

    APPEND_STRING,// special string concat operator

    COMMA,
    SEMICOLON,

    OPEN_CALL,
    OPEN_BLOCK,
    OPEN_ARRAY,

    CLOSE_CALL,
    CLOSE_BLOCK,
    CLOSE_ARRAY,
    ;

    companion object {
        fun findTokenType(string: String): TokenType {
            return when (string) {
                "(" -> OPEN_CALL
                "[" -> OPEN_ARRAY
                "{" -> OPEN_BLOCK
                ")" -> CLOSE_CALL
                "]" -> CLOSE_ARRAY
                "}" -> CLOSE_BLOCK
                "," -> COMMA
                ";" -> SEMICOLON
                "@" -> LABEL
                else -> {
                    val first = string.first()
                    when {
                        first == '"' -> STRING
                        first == '\'' -> NUMBER
                        first == '`' -> NAME
                        string in ZauberTokenizer.KEYWORDS -> KEYWORD
                        first in 'A'..'Z' ||
                                first in 'a'..'z' || first in "_`" -> NAME
                        string.any { it in '0'..'9' } -> NUMBER
                        else -> SYMBOL
                    }
                }
            }
        }
    }
}