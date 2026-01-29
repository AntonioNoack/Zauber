package me.anno.cpp.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.tokenizer.Tokenizer

class CppTokenizer(
    src: String,
    fileName: String,
    val isCNotCxx: Boolean
) : Tokenizer(src, fileName) {

    companion object {

        val cKeywords = setOf(
            "auto", "break", "case", "char", "const", "continue",
            "default", "do", "double", "else", "enum", "extern",
            "float", "for", "goto", "if", "inline", "int", "long",
            "register", "restrict", "return", "short", "signed",
            "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while",
            "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex",
            "_Generic", "_Imaginary", "_Noreturn", "_Static_assert",
            "_Thread_local"
        )

        val cppKeywords = cKeywords + setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "bitand",
            "bitor", "bool", "catch", "char16_t", "char32_t",
            "class", "compl", "constexpr", "decltype", "delete",
            "dynamic_cast", "explicit", "export", "false",
            "friend", "mutable", "namespace", "new", "noexcept",
            "not", "not_eq", "nullptr", "operator", "or",
            "or_eq", "private", "protected", "public",
            "reinterpret_cast", "static_assert", "static_cast",
            "template", "this", "thread_local", "throw", "true",
            "try", "typeid", "typename", "using", "virtual",
            "wchar_t", "xor", "xor_eq"
        )
    }

    override fun tokenize(): TokenList {
        while (i < n) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++

                // comments
                c == '/' && i + 1 < n && src[i + 1] == '/' -> skipLineComment()
                c == '/' && i + 1 < n && src[i + 1] == '*' -> skipBlockComment()

                // identifiers
                c.isLetter() || c == '_' -> readIdentifier()

                // numbers
                c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit()) -> readNumber()

                // literals
                c == '"' -> readString()
                c == '\'' -> readChar()

                // brackets
                c == '(' -> tokens.add(TokenType.OPEN_CALL, i++, i)
                c == ')' -> tokens.add(TokenType.CLOSE_CALL, i++, i)
                c == '{' -> tokens.add(TokenType.OPEN_BLOCK, i++, i)
                c == '}' -> tokens.add(TokenType.CLOSE_BLOCK, i++, i)
                c == '[' -> tokens.add(TokenType.OPEN_ARRAY, i++, i)
                c == ']' -> tokens.add(TokenType.CLOSE_ARRAY, i++, i)

                c == ',' -> tokens.add(TokenType.COMMA, i++, i)

                // symbols (single-char ONLY)
                else -> tokens.add(TokenType.SYMBOL, i++, i)
            }
        }

        convertKeywords()
        return tokens
    }

    private fun readIdentifier() {
        val start = i++
        while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
        tokens.add(TokenType.NAME, start, i)
    }

    private fun readNumber() {
        val start = i++
        // todo I think C supports octal numbers and booleans, too
        if (src[start] == '0' && i < n && src[i] in "xX") {
            i++
            while (i < n && (src[i].isDigit() || src[i] in "abcdefABCDEF_")) i++
        } else {
            while (i < n && (src[i].isDigit() || src[i] == '_')) i++
            if (i < n && src[i] == '.') {
                i++
                while (i < n && src[i].isDigit()) i++
            }
            if (i < n && src[i] in "eE") {
                i++
                if (i < n && src[i] in "+-") i++
                while (i < n && src[i].isDigit()) i++
            }
        }
        while (i < n && src[i] in "uUlLfF") i++
        tokens.add(TokenType.NUMBER, start, i)
    }

    private fun readString() {
        val start = i++
        while (i < n) {
            when (src[i]) {
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

    private fun readChar() {
        val start = i++
        if (i < n && src[i] == '\\') i += 2
        else i++
        if (i < n && src[i] == '\'') i++
        tokens.add(TokenType.NUMBER, start, i)
    }

    private fun convertKeywords() {
        val keywords = if (isCNotCxx) cKeywords else cppKeywords
        for (i in 0 until tokens.size) {
            if (tokens.getType(i) == TokenType.NAME &&
                tokens.toString(i) in keywords
            ) {
                tokens.setType(i, TokenType.KEYWORD)
            }
        }
    }
}
