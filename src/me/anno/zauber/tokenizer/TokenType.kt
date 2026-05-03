package me.anno.zauber.tokenizer

import me.anno.utils.StringStyles

enum class TokenType(val style: String) {

    NAME(StringStyles.TEXT), // starts with A-Za-z_
    STRING(StringStyles.GREEN),
    NUMBER(StringStyles.BLUE), // starts with 0-9.; a char is a special number
    SYMBOL(StringStyles.YELLOW), // anything like +-*/=&%$§

    KEYWORD(StringStyles.ORANGE),

    APPEND_STRING(StringStyles.GREEN),// special string concat operator

    COMMA(StringStyles.ORANGE),
    SEMICOLON(StringStyles.ORANGE),

    OPEN_CALL(StringStyles.WHITE),
    OPEN_BLOCK(StringStyles.WHITE),
    OPEN_ARRAY(StringStyles.WHITE),

    CLOSE_CALL(StringStyles.WHITE),
    CLOSE_BLOCK(StringStyles.WHITE),
    CLOSE_ARRAY(StringStyles.WHITE),

    // python-exclusive:
    INDENT(""),
    DEDENT(""),
    ;
}