package me.anno.zauber.tokenizer

enum class TokenType {

    NAME, // starts with A-Za-z_
    STRING,
    NUMBER, // starts with 0-9.; a char is a special number
    SYMBOL, // anything like +-*/=&%$§

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

    // python-exclusive:
    INDENT,
    DEDENT,
    ;
}