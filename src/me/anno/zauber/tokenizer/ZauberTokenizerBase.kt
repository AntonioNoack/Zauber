package me.anno.zauber.tokenizer

abstract class ZauberTokenizerBase(
    src: String, fileName: String,
    val keywords: Set<String>,
) : Tokenizer(src, fileName) {

    var supportsTickedNames = false
    var hasNotAsNotIn = false
    var supportsDollarInName = false

    val hasNonSealedKeyword = "non-sealed" in keywords

    override fun tokenize(): TokenList {
        val nameLetters = if (supportsDollarInName) "_$" else "_"
        while (i < n) {
            val c = src[i]
            when {
                // c == '\n' -> list.add(TokenType.LINE_BREAK, i++, i)
                c.isWhitespace() -> i++ // skip spaces

                // comments
                c == '/' && i + 1 < n && src[i + 1] == '/' -> skipLineComment()
                c == '/' && i + 1 < n && src[i + 1] == '*' -> skipBlockComment()

                // identifiers
                c.isLetter() || c in nameLetters -> {
                    val start = i
                    i++
                    while (i < n && (src[i].isLetterOrDigit() || src[i] in nameLetters)) i++
                    if (i < n && src[i] == '@') {
                        tokens.add(TokenType.LABEL, start, i)
                        i++
                    } else {
                        // hack for non-sealed
                        if (hasNonSealedKeyword && i - start == 3 &&
                            src.startsWith("non-sealed", start) &&
                            (start + "non-sealed".length < src.length ||
                                    src[start + "non-sealed".length].isWhitespace())
                        ) {
                            i = start + "non-sealed".length
                            tokens.add(TokenType.KEYWORD, start, i)
                        } else {
                            tokens.add(TokenType.NAME, start, i)
                        }
                    }
                }

                c == '`' && supportsTickedNames -> {
                    val start = i++
                    while (i < n && src[i] != '`') i++
                    i++ // skip end '`'
                    tokens.add(TokenType.NAME, start, i)
                }

                // numbers
                c.isDigit() -> readNumber()

                // char literal = number
                c == '\'' -> {
                    val start = i++
                    if (i < n && src[i] == '\\') i += 2
                    while (i < n && src[i] != '\'') i++
                    if (i < n) i++ // skip \'
                    tokens.add(TokenType.NUMBER, start, i)
                }

                // string with interpolation
                c == '"' -> parseString()

                // special one-char tokens
                c in ",;()[]{}" -> {
                    val type = when (c) {
                        ',' -> TokenType.COMMA
                        ';' -> TokenType.SEMICOLON
                        '(' -> TokenType.OPEN_CALL
                        ')' -> TokenType.CLOSE_CALL
                        '{' -> TokenType.OPEN_BLOCK
                        '}' -> TokenType.CLOSE_BLOCK
                        '[' -> TokenType.OPEN_ARRAY
                        ']' -> TokenType.CLOSE_ARRAY
                        else -> throw IllegalStateException()
                    }
                    tokens.add(type, i, i + 1)
                    i++
                }

                c == '?' && hasNotAsNotIn -> {
                    // parse 'as?'
                    if (tokens.size > 0 && tokens.equals(tokens.size - 1, "as")) {
                        val i0 = tokens.getI0(tokens.size - 1)
                        tokens.removeLast()
                        tokens.add(TokenType.SYMBOL, i0, ++i)
                    } else tokens.add(TokenType.SYMBOL, i++, i)
                }

                c == '!' && hasNotAsNotIn -> {
                    // parse !in and !is
                    if (i + 3 < src.length && src[i + 1] == 'i' && src[i + 2] in "sn" && src[i + 3].isWhitespace()) {
                        tokens.add(TokenType.SYMBOL, i, i + 3)
                        i += 3
                    } else tokens.add(TokenType.SYMBOL, i++, i)
                }

                c == '.' -> {
                    if (i + 1 < src.length && src[i + 1].isDigit() &&
                        !(i > 0 && src[i - 1] == '.')
                    ) {
                        readNumber()
                    } else tokens.add(TokenType.SYMBOL, i++, i)
                }

                // symbols
                else -> tokens.add(TokenType.SYMBOL, i++, i)
            }
        }
        convertHardKeywords()
        return tokens
    }

    fun readInteger() {
        while (i < n && (src[i] in '0'..'9' || src[i] == '_')) i++
    }

    fun readExponent() {
        if (i < n && src[i] in "eE") {
            i++
            if (src[i] in "+-") i++
            readInteger()
        }
    }

    fun readBinaryExponent() {
        if (i < n && src[i] in "pP") {
            i++
            if (src[i] in "+-") i++
            readInteger()
        }
    }

    fun readCommaDigits() {
        if (i + 1 < n && src[i] == '.' && src[i + 1] in '0'..'9') {
            i += 2
            readInteger()
        }
    }

    fun readNumber() {
        val start = i++
        var first = src[start]
        if (first in "+-") {
            first = src[i]
            i++
        }
        @Suppress("IntroduceWhenSubject")
        when {
            first == '0' && i < n && src[i] in "xX" -> {
                i++ // skip 0x
                while (i < n && (src[i] in '0'..'9' || src[i] in "abcdefABCDEF_")) i++
                if (i + 1 < n && src[i] == '.' && src[i + 1] in '0'..'9') {
                    i++
                    while (i < n && (src[i] in '0'..'9' || src[i] in "abcdefABCDEF_")) i++
                }
                readBinaryExponent()
            }
            first == '0' && i < n && src[i] in "bB" -> {
                i++ // skip 0b
                while (i < n && (src[i] in "01_")) i++
            }
            first == '.' -> {
                readInteger()
                readExponent()
            }
            else -> {
                readInteger()
                readCommaDigits()
                readExponent()
            }
        }
        if (i < n && src[i] in "lLuUfFdDhH") i++
        tokens.add(TokenType.NUMBER, start, i)
    }

    fun convertHardKeywords() {
        for (i in 0 until tokens.size) {
            if (tokens.getType(i) == TokenType.NAME) {
                val asString = tokens.toString(i)
                if (asString in keywords) {
                    tokens.setType(i, TokenType.KEYWORD)
                }
            }
        }
    }

    fun skipBlock() {
        check(src[i - 1] == '{')
        var depth = 1
        loop@ while (i < n && depth > 0) {
            when (src[i]) {
                in "([{" -> depth++
                in ")]}" -> depth--
                '/' if (i + 1 < src.length && src[i + 1] == '/') -> skipLineComment()
                '/' if (i + 1 < src.length && src[i + 1] == '*') -> skipBlockComment()
                '"' -> {
                    // skip string
                    val size = tokens.size
                    parseString()
                    tokens.size = size
                    continue@loop // must not call i++
                }
            }
            i++
        }
        check(src[i - 1] == '}')
    }

    open fun parseString() {
        val open = i
        i++ // skip initial "
        tokens.add(TokenType.OPEN_CALL, open, open + 1)

        val chunkStart = i
        while (i < n) {
            val ch = src[i]
            when (ch) {
                '\\' -> i += 2 // skip escaped char
                '"' -> {
                    tokens.add(TokenType.STRING, chunkStart, i)
                    i++ // skip closing "
                    tokens.add(TokenType.CLOSE_CALL, i - 1, i)
                    return
                }
                else -> i++
            }
        }
    }
}