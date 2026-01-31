package me.anno.zauber.tokenizer

class ZauberTokenizer(src: String, fileName: String) :
    ZauberTokenizerBase(src, fileName, KEYWORDS) {

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

    override fun parseString() {
        val open = i
        i++ // skip initial "
        tokens.add(TokenType.OPEN_CALL, open, open + 1)

        var chunkStart = i
        fun flushChunk(until: Int) {
            tokens.add(TokenType.STRING, chunkStart, until)
        }

        while (i < n) {
            val ch = src[i]
            when (ch) {
                '\\' -> i += 2 // skip escaped char
                '"' -> {
                    flushChunk(i)

                    i++ // skip closing "
                    tokens.add(TokenType.CLOSE_CALL, i - 1, i)
                    return
                }
                '$' if i + 1 < n && (src[i + 1].isLetter() || src[i + 1] == '{') -> {
                    flushChunk(i)

                    // Begin: + ( ... )
                    tokens.add(TokenType.APPEND_STRING, i, i + 1)
                    tokens.add(TokenType.OPEN_CALL, i, i)

                    i++ // consume $
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