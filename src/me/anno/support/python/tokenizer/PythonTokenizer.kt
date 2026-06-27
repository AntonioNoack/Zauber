package me.anno.support.python.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class PythonTokenizer(val source: String, fileName: String) {

    companion object {
        private val KEYWORDS = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "case"
        )
    }

    private var size = source.length
    private var i = 0

    private val tokens = TokenList(source, fileName)

    private val indentStack = ArrayDeque<Int>().apply { addLast(0) }
    private var atLineStart = true

    var bracketDepth = 0

    fun tokenizeImpl() {
        while (i < size) {

            if (atLineStart) handleIndentation()
            if (i >= size) break

            val c = source[i]
            when {
                c == '\n' -> {
                    i++
                    atLineStart = true
                }

                c.isWhitespace() -> i++
                c == '#' -> skipComment()
                isStringStart(i) -> readString()
                c.isLetter() || c == '_' -> readIdentifier()
                c.isDigit() -> readNumber()
                c in "(),;[]{}" -> {
                    val type = when (c) {
                        ',' -> TokenType.COMMA
                        ';' -> TokenType.SEMICOLON
                        '(' -> {
                            bracketDepth++
                            TokenType.OPEN_CALL
                        }
                        ')' -> {
                            bracketDepth--
                            TokenType.CLOSE_CALL
                        }
                        '[' -> {
                            bracketDepth++
                            TokenType.OPEN_ARRAY
                        }
                        ']' -> {
                            bracketDepth--
                            TokenType.CLOSE_ARRAY
                        }
                        '{' -> TokenType.OPEN_BLOCK
                        '}' -> TokenType.CLOSE_BLOCK
                        else -> error("Invalid state")
                    }
                    tokens.add(type, i, i + 1)
                    i++
                }
                c == '.' -> {
                    if (i + 1 < size && source[i + 1].isDigit()) readNumber()
                    else tokens.add(TokenType.SYMBOL, i++, i)
                }
                c == '\\' && i + 1 < source.length && source[i + 1].isWhitespace() -> {
                    i += 2 // just skip it
                    while (source[i].isWhitespace()) i++ // skip all whitespace after, too
                }
                else -> tokens.add(TokenType.SYMBOL, i++, i)
            }
        }
    }

    fun tokenize(): TokenList {
        tokenizeImpl()

        // flush remaining dedents
        while (indentStack.size > 1) {
            indentStack.removeLast()
            tokens.add(TokenType.DEDENT, i, i)
        }

        convertKeywords()
        return tokens
    }

    /**
     * indentation logic (CPython-like)
     * */
    private fun handleIndentation() {
        var j = i
        var spaces = 0

        while (j < size) {
            when (source[j]) {
                ' ' -> spaces++
                '\t' -> spaces += 8 // CPython rule (tab = 8)
                else -> break
            }
            j++
        }

        if (j < size && source[j] == '\n') {
            i = j
            return
        }

        val current = indentStack.last()
        if (bracketDepth == 0) when {
            spaces > current -> {
                indentStack.addLast(spaces)
                tokens.add(TokenType.INDENT, i, j)
            }
            spaces < current -> {
                while (indentStack.isNotEmpty() && indentStack.last() > spaces) {
                    indentStack.removeLast()
                    tokens.add(TokenType.DEDENT, i, j)
                }
            }
        }

        i = j
        atLineStart = false
    }

    private fun readIdentifier() {
        val start = i++
        while (i < size && (source[i].isLetterOrDigit() || source[i] == '_')) i++
        tokens.add(TokenType.NAME, start, i)
    }

    private fun skipComment() {
        val start = i
        while (i < size && source[i] != '\n') i++
        tokens.addComment(start, i)
    }

    private fun readNumber() {
        val start = i

        if (source[i] == '0' && i + 1 < size) {
            when (source[i + 1]) {
                'x', 'X' -> {
                    i += 2
                    while (i < size && (source[i].isDigit() || source[i] in "abcdefABCDEF_")) i++
                }
                'b', 'B' -> {
                    i += 2
                    while (i < size && source[i] in "01_") i++
                }
                'o', 'O' -> {
                    i += 2
                    while (i < size && source[i] in "01234567_") i++
                }
                else -> readDecimal()
            }
        } else readDecimal()

        tokens.add(TokenType.NUMBER, start, i)
    }

    private fun readDecimal() {
        while (i < size && (source[i].isDigit() || source[i] == '_')) i++
        if (i < size && source[i] == '.') {
            i++
            while (i < size && (source[i].isDigit() || source[i] == '_')) i++
        }
        if (i < size && source[i] in "eE") {
            i++
            if (i < size && source[i] in "+-") i++
            while (i < size && (source[i].isDigit() || source[i] == '_')) i++
        }
    }

    // Strings and F-Strings
    private fun isStringStart(pos: Int): Boolean {
        var j = pos
        while (j < size && source[j].lowercaseChar() in "rfbu") j++
        return j < size && (source[j] == '"' || source[j] == '\'')
    }

    private fun readString() {
        val start = i

        var isF = false

        while (i < size && source[i].lowercaseChar() in "rfbu") {
            if (source[i].lowercaseChar() == 'f') isF = true
            i++
        }

        val quote = source[i]
        val triple = i + 2 < size && source[i + 1] == quote && source[i + 2] == quote
        i += if (triple) 3 else 1

        if (isF) {
            readFString(start, quote, triple)
        } else {
            readPlainString(start, quote, triple)
        }
    }

    private fun readPlainString(start: Int, quote: Char, triple: Boolean) {
        while (i < size) {
            val c = source[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) {
                if (triple) {
                    if (source.startsWith("$quote$quote$quote", i)) {
                        i += 3
                        break
                    }
                } else {
                    i++
                    break
                }
            }
            i++
        }
        tokens.add(TokenType.STRING, start, i)
    }

    // full F-String parser
    private fun readFString(start: Int, quote: Char, triple: Boolean) {

        tokens.add(TokenType.KEYWORD, start, start + 1)
        tokens.add(TokenType.OPEN_CALL, start + 1, start + 1)

        var chunkStart = i

        fun flushChunk(end: Int) {
            if (end > chunkStart)
                tokens.add(TokenType.STRING, chunkStart, end)
        }

        while (i < size) {
            when (source[i]) {
                '\\' -> i += 2
                '{' -> {
                    if (i + 1 < size && source[i + 1] == '{') {
                        i += 2 // escaped {{
                        continue
                    }

                    flushChunk(i)

                    tokens.add(TokenType.APPEND_STRING, i, i + 1)
                    tokens.add(TokenType.OPEN_CALL, i, i)

                    val exprStart = ++i
                    val exprEnd = findFExprEnd()

                    // val oldI = i
                    val oldSize = size
                    i = exprStart
                    size = exprEnd

                    // recursive tokenize expression
                    bracketDepth++
                    tokenizeImpl()
                    bracketDepth--

                    // i = oldI
                    check(i == exprEnd)
                    i = exprEnd + 1
                    size = oldSize

                    tokens.add(TokenType.CLOSE_CALL, i, i)
                    tokens.add(TokenType.APPEND_STRING, i, i)

                    chunkStart = i
                }
                '}' -> {
                    if (i + 1 < size && source[i + 1] == '}') {
                        i += 2
                        continue
                    } else i++
                }
                quote -> {
                    if (triple) {
                        if (!source.startsWith("$quote$quote$quote", i)) {
                            i++
                            continue
                        }
                        flushChunk(i)
                        i += 3
                        tokens.add(TokenType.CLOSE_CALL, i - 1, i)
                        return
                    } else {
                        flushChunk(i)
                        i++
                        tokens.add(TokenType.CLOSE_CALL, i - 1, i)
                        return
                    }
                }
                else -> i++
            }
        }
    }

    private fun findFExprEnd(): Int {
        var depth = 0
        var j = i

        while (j < size) {
            when (val start = source[j]) {
                in "([{" -> depth++
                in ")]}" -> {
                    if (depth == 0) return j
                    depth--
                }
                in "'\"" -> {
                    j++
                    while (j < size && source[j] != start) {
                        j += if (source[j] == '\\') 2 // skip
                        else 1
                    }
                }
            }
            j++
        }
        return size
    }

    private fun convertKeywords() {
        for (k in 0 until tokens.size) {
            if (tokens.getType(k) == TokenType.NAME) {
                if (tokens.toString(k) in KEYWORDS) {
                    tokens.setType(k, TokenType.KEYWORD)
                }
            }
        }
    }

}