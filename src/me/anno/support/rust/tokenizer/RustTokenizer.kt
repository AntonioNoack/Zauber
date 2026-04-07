package me.anno.support.rust.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class RustTokenizer(val source: String, fileName: String) {

    companion object {
        private val KEYWORDS = setOf(
            "as", "break", "const", "continue", "crate", "else", "enum", "extern",
            "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
            "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct",
            "super", "trait", "true", "type", "unsafe", "use", "where", "while",
            "async", "await", "dyn"
        )
    }

    private val n = source.length
    private var i = 0

    private val tokens = TokenList(source, fileName)

    fun tokenize(): TokenList {
        while (i < n) {
            val c = source[i]

            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < n && source[i + 1] == '/' -> skipLineComment()
                c == '/' && i + 1 < n && source[i + 1] == '*' -> skipBlockComment()

                // raw string r"...", r#"..."#
                c == 'r' && i + 1 < n && (source[i + 1] == '"' || source[i + 1] == '#') -> {
                    readRawString()
                }

                c.isLetter() || c == '_' -> readIdentifier()
                c == '\'' -> readLifetimeOrChar()
                c.isDigit() -> readNumber()
                c == '"' -> readString()

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
                        else -> error("unreachable")
                    }
                    tokens.add(type, i, i + 1)
                    i++
                }

                // symbols
                else -> tokens.add(TokenType.SYMBOL, i++, i)
            }
        }

        convertKeywords()
        return tokens
    }

    private fun readIdentifier() {
        val start = i++
        while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
        tokens.add(TokenType.NAME, start, i)
    }

    private fun convertKeywords() {
        for (j in 0 until tokens.size) {
            if (tokens.getType(j) == TokenType.NAME) {
                val s = tokens.toString(j)
                if (s in KEYWORDS) {
                    tokens.setType(j, TokenType.KEYWORD)
                }
            }
        }
    }

    private fun skipLineComment() {
        val start = i
        i += 2
        while (i < n && source[i] != '\n') i++
        tokens.addComment(start, i)
    }

    private fun skipBlockComment() {
        val start = i
        i += 2
        var depth = 1

        while (i < n && depth > 0) {
            when {
                i + 1 < n && source[i] == '/' && source[i + 1] == '*' -> {
                    depth++
                    i += 2
                }
                i + 1 < n && source[i] == '*' && source[i + 1] == '/' -> {
                    depth--
                    i += 2
                }
                else -> i++
            }
        }

        tokens.addComment(start, i)
    }

    private fun readString() {
        val start = i++
        while (i < n) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> {
                    i++
                    break
                }
                else -> i++
            }
        }
        tokens.add(TokenType.STRING, start, i)
    }

    private fun readRawString() {
        val start = i++
        var hashes = 0

        while (i < n && source[i] == '#') {
            hashes++
            i++
        }

        if (i >= n || source[i] != '"') {
            tokens.add(TokenType.NAME, start, i)
            return
        }

        i++ // skip opening quote

        loop@ while (i < n) {
            if (source[i] == '"') {
                var j = i + 1
                var count = 0
                while (j < n && source[j] == '#') {
                    count++
                    j++
                }
                if (count == hashes) {
                    i = j
                    break@loop
                }
            }
            i++
        }

        tokens.add(TokenType.STRING, start, i)
    }

    private fun readNumber() {
        val start = i++
        if (source[start] == '0' && i < n) {
            when (source[i]) {
                'x', 'X' -> {
                    i++
                    while (i < n && (source[i].isDigit() || source[i] in "abcdefABCDEF_")) i++
                }
                'b', 'B' -> {
                    i++
                    while (i < n && source[i] in "01_") i++
                }
                'o', 'O' -> {
                    i++
                    while (i < n && source[i] in "01234567_") i++
                }
            }
        } else {
            while (i < n && (source[i].isDigit() || source[i] == '_')) i++
            if (i < n && source[i] == '.') {
                i++
                while (i < n && (source[i].isDigit() || source[i] == '_')) i++
            }
        }

        // suffix (u32, i64, f32, etc.)
        while (i < n && source[i].isLetterOrDigit()) i++

        tokens.add(TokenType.NUMBER, start, i)
    }

    private fun readLifetimeOrChar() {
        val start = i++
        if (i < n && source[i].isLetter() && (i + 1 >= n || source[i + 1] != '\'')) {
            // lifetime: 'a
            while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
            tokens.add(TokenType.NAME, start, i)
        } else {
            // char literal
            if (i < n && source[i] == '\\') i += 2
            while (i < n && source[i] != '\'') i++
            if (i < n) i++
            tokens.add(TokenType.NUMBER, start, i)
        }
    }
}