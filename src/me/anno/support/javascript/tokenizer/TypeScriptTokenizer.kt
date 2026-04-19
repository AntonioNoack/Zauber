package me.anno.support.javascript.tokenizer

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.tokenizer.Tokenizer

/**
 * Chatchy-generated tokenizer to get started...
 * */
class TypeScriptTokenizer(
    src: String,
    fileName: String
) : Tokenizer(src, fileName) {

    companion object {
        val KEYWORDS = setOf(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "yield", "let", "static", "implements", "interface", "package", "private",
            "protected", "public",

            // TypeScript
            "as", "any", "unknown", "never", "number", "string", "boolean", "symbol", "bigint",
            "type", "from", "of", "readonly", "keyof", "infer", "is", "asserts", "namespace",
            "abstract", "override", "satisfies"
        )

        val OPERATORS = setOf(
            "==", "===", "!=", "!==", "<=", ">=", "&&", "||", "??",
            "++", "--", "=>", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", "<<", ">>", ">>>", "<<=", ">>=", ">>>=",
            "...", "?."
        )
    }

    private var lastTokenCanEndExpression = false

    override fun tokenize(): TokenList {
        while (i < n) {
            val c = src[i]

            when {
                c.isWhitespace() -> i++

                // comments
                c == '/' && peek(1) == '/' -> skipLineComment()
                c == '/' && peek(1) == '*' -> skipBlockComment()

                // strings
                c == '"' || c == '\'' -> readString(c)

                // template literals
                c == '`' -> readTemplate()

                // numbers
                c.isDigit() -> readNumber()

                // identifiers / keywords
                isIdentifierStart(c) -> readIdentifier()

                // regex vs division
                c == '/' -> {
                    if (isRegexAllowed()) readRegex()
                    else {
                        tokens.add(TokenType.SYMBOL, i, i + 1)
                        lastTokenCanEndExpression = false
                        i++
                    }
                }

                // punctuation
                c in "(){},;[]:" -> addPunctuation(c)

                // operators
                else -> readOperator()
            }
        }

        convertKeywords()
        return tokens
    }

    private fun isIdentifierStart(c: Char) =
        c.isLetter() || c == '_' || c == '$'

    private fun isIdentifierPart(c: Char) =
        c.isLetterOrDigit() || c == '_' || c == '$'

    private fun readIdentifier() {
        val start = i++
        while (i < n && isIdentifierPart(src[i])) i++

        tokens.add(TokenType.NAME, start, i)
        lastTokenCanEndExpression = true
    }

    private fun readString(quote: Char) {
        val start = i++
        while (i < n) {
            val c = src[i]
            if (c == '\\') i += 2
            else if (c == quote) {
                i++
                break
            } else i++
        }
        tokens.add(TokenType.STRING, start, i)
        lastTokenCanEndExpression = true
    }

    private fun readTemplate() {
        val start = i++
        tokens.add(TokenType.STRING, start, start + 1)

        while (i < n) {
            when (src[i]) {
                '`' -> {
                    i++
                    tokens.add(TokenType.STRING, i - 1, i)
                    return
                }
                '\\' -> i += 2
                '$' if peek(1) == '{' -> {
                    tokens.add(TokenType.APPEND_STRING, i, i + 2)
                    i += 2

                    val exprStart = i
                    skipTemplateExpr()
                    val exprEnd = i - 1

                    val oldI = i
                    val oldN = n

                    i = exprStart
                    n = exprEnd
                    tokenize()

                    i = oldI
                    n = oldN
                }
                else -> i++
            }
        }
    }

    private fun skipTemplateExpr() {
        var depth = 1
        while (i < n && depth > 0) {
            when (src[i]) {
                '{' -> depth++
                '}' -> depth--
                '"', '\'' -> readString(src[i])
                '`' -> readTemplate()
            }
            i++
        }
    }

    private fun isRegexAllowed(): Boolean {
        return !lastTokenCanEndExpression
    }

    private fun readRegex() {
        val start = i++
        var inClass = false

        while (i < n) {
            val c = src[i]
            when {
                c == '\\' -> i += 2
                c == '[' -> {
                    inClass = true; i++
                }
                c == ']' -> {
                    inClass = false; i++
                }
                c == '/' && !inClass -> {
                    i++
                    break
                }
                else -> i++
            }
        }

        while (i < n && src[i].isLetter()) i++

        tokens.add(TokenType.STRING, start, i)
        lastTokenCanEndExpression = true
    }

    private fun readNumber() {
        val start = i++

        while (i < n && (src[i].isDigit() || src[i] == '_')) i++

        if (i < n && src[i] == '.') {
            i++
            while (i < n && src[i].isDigit()) i++
        }

        if (i < n && src[i] in "eE") {
            i++
            if (src[i] in "+-") i++
            while (i < n && src[i].isDigit()) i++
        }

        tokens.add(TokenType.NUMBER, start, i)
        lastTokenCanEndExpression = true
    }

    private fun readOperator() {
        val start = i

        var best: String? = null

        for (op in OPERATORS) {
            if (src.startsWith(op, i)) {
                if (best == null || op.length > best.length) {
                    best = op
                }
            }
        }

        if (best != null) {
            i += best.length
        } else {
            i++
        }

        tokens.add(TokenType.SYMBOL, start, i)
        lastTokenCanEndExpression = false
    }

    private fun addPunctuation(c: Char) {
        val type = when (c) {
            '(' -> TokenType.OPEN_CALL
            ')' -> TokenType.CLOSE_CALL
            '{' -> TokenType.OPEN_BLOCK
            '}' -> TokenType.CLOSE_BLOCK
            '[' -> TokenType.OPEN_ARRAY
            ']' -> TokenType.CLOSE_ARRAY
            ',' -> TokenType.COMMA
            ';' -> TokenType.SEMICOLON
            else -> TokenType.SYMBOL
        }

        tokens.add(type, i, i + 1)
        i++

        lastTokenCanEndExpression = c == ')' || c == ']' || c == '}'
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

    private fun peek(offset: Int): Char =
        if (i + offset < n) src[i + offset] else ' '

}