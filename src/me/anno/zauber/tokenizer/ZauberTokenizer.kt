package me.anno.zauber.tokenizer

class ZauberTokenizer(src: String, fileName: String) :
    ZauberTokenizerBase(src, fileName, KEYWORDS, "lLuUfFdDhH") {

    companion object {
        private val KEYWORDS = setOf(
            "true", "false", "null",
            "class", "interface", "object",
            "package", "import",
            "val", "var", "vararg", "lateinit", "fun",
            "abstract", "override",

            "if", "else", "do", "while", "when", "for",
            "break", "continue",
            "return", "throw", "yield",
            "in", "!in", "is", "!is", "as", "as?",

            "super", "this",
            "typealias",
            "try", "catch", "finally",

            // keywords custom to Zauber:
            "defer", "errdefer", "async"
        )
    }

    init {
        supportsTickedNames = true
        hasNotAsNotIn = true
    }

    fun hasDollarDepth(dollarDepth: Int): Boolean {
        if (i + dollarDepth > src.length) return false
        for (di in 0 until dollarDepth) {
            if (src[i + di] != '$') return false
        }
        return true
    }

    fun findDollarDepth(): Int {
        var ctr = 1
        var i = i
        while (i > 0 && src[i - 1] == '$') {
            i--
            ctr++
        }
        // todo remove all symbol tokens, that are '$' for dollar-depth
        return ctr
    }

    override fun parseString() {
        val dollarDepth = findDollarDepth()
        val isTripleString = i + 2 < src.length && src[i + 1] == '"' && src[i + 2] == '"'

        val open = i
        i += if (isTripleString) 3 else 1 // skip initial "
        tokens.add(TokenType.OPEN_CALL, open, i)

        var chunkStart = i
        fun flushChunk(until: Int) {
            tokens.add(TokenType.STRING, chunkStart, until)
        }

        while (i < n) {
            val ch = src[i]
            when (ch) {
                '\\' -> i += 2 // skip escaped char
                '"' -> {
                    if (isTripleString && !src.startsWith("\"\"\"", i)) {
                        i++
                        continue
                    }

                    flushChunk(i)

                    i++ // skip closing "
                    tokens.add(TokenType.CLOSE_CALL, i - 1, i)
                    return
                }
                '$' if hasDollarDepth(dollarDepth) &&
                        i + dollarDepth < n &&
                        (src[i + dollarDepth].isLetter() || src[i + dollarDepth] == '{') -> {

                    // todo compare escape length
                    flushChunk(i)

                    // Begin: + ( ... )
                    tokens.add(TokenType.APPEND_STRING, i, i + 1)
                    tokens.add(TokenType.OPEN_CALL, i, i)

                    i += dollarDepth // consume $
                    if (i < n && src[i] == '{') {
                        // ${ expr }
                        i++ // skip {
                        val innerStart = i
                        skipBlock()
                        val innerEnd = i - 1

                        val oldI = i
                        val oldN = n
                        i = innerStart
                        n = innerEnd

                        // tokenize substring recursively
                        tokenize()

                        i = oldI
                        n = oldN

                    } else {
                        // $name
                        val start = i
                        i++
                        while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                        tokens.add(TokenType.NAME, start, i)
                    }

                    // End: )
                    tokens.add(TokenType.CLOSE_CALL, i, i)
                    tokens.add(TokenType.APPEND_STRING, i, i)

                    chunkStart = i
                }
                else -> i++
            }
        }
    }
}