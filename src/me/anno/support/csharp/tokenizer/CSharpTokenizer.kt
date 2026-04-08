package me.anno.support.csharp.tokenizer

import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.tokenizer.ZauberTokenizerBase

class CSharpTokenizer(src: String, fileName: String) :
    ZauberTokenizerBase(src, fileName, KEYWORDS, "lLuUfFdDmM") {

    init {
        supportsDollarInName = true
    }

    companion object {
        @Suppress("SpellCheckingInspection")
        private val KEYWORDS = setOf(
            // todo type "decimal" (mM suffix, 16-byte type to represent decimals accurately)
            "abstract", "as", "base", "break", "case", "catch", "checked", "class",
            "const", "continue", "default", "delegate", "do", "else", "enum", "event",
            "explicit", "extern", "false", "finally", "fixed", "for", "foreach", "goto", "if",
            "implicit", "in", "interface", "internal", "is", "lock", "namespace",
            "new", "null", "object", "operator", "out", "override", "params", "private", "protected",
            "public", "readonly", "ref", "return", "sealed", "sizeof", "stackalloc",
            "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof",
            "unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile", "while",
            "add", "allows", "alias", "and", "ascending", "args", "async", "await", "by", "descending",
            "dynamic", "equals", "extension", "field", "file", "from", "get", "global", "group",
            "init", "into", "join", "let", "managed", "nameof", "not", "notnull",
            "on", "or", "orderby", "partial", "partial", "record", "remove", "required", "scoped",
            "select", "set", "unmanaged", "unmanaged", "value", "var", "when", "where", "where", "with", "yield"
        )
    }

    override fun parseString() {
        // todo why is this called from inside a comment???
        check(src[i] == '"')
        if (i > 0 && src[i - 1] == '@') {
            // read "verbatim" string
            // todo we somehow have $@-strings, whatever they are...
            if (tokens.equals(tokens.size - 1, "@")) {
                tokens.removeLast()
            }

            val i0 = i - 1 // true start
            i++ // skip single-quote
            while (i < n) {
                when (src[i++]) {
                    '"' -> {
                        if (src[i] == '"') {
                            i++ // skip it
                        } else {
                            // found valid end
                            tokens.add(TokenType.STRING, i0, i)
                            return
                        }
                    }
                    // {{ and }} produce just { and }
                    // any other escape sequence is ignored
                }
            }
            throw IllegalStateException("Verbatim string without end at ${tokens.err(tokens.size - 1)}")
        } else if (i + 2 < src.length && src[i + 1] == '"' && src[i + 2] == '"') {
            val eos = src.indexOf("\"\"\"", i + 3)
            if (eos < 0) {
                tokens.add(TokenType.STRING, i, i)
                throw IllegalStateException("Failed to find end of triple-string at ${tokens.err(tokens.size - 1)}")
            }
            tokens.add(TokenType.STRING, i, eos + 3)
            i = eos + 3
        } else super.parseString()
    }
}