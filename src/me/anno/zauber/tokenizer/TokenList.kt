package me.anno.zauber.tokenizer

import me.anno.utils.Maths.clamp
import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.parser.SemanticTokenList
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Import
import kotlin.math.max
import kotlin.math.min

class TokenList(val source: CharSequence, val fileName: String) {

    companion object {
        private val LOGGER = LogManager.getLogger(TokenList::class)
        private val i0 = IntArray(0)
    }

    var semantic: SemanticTokenList? = null

    var size = 0
    var tliIndex = -1

    private var tokenTypes = ByteArray(16)
    private var offsets = IntArray(32)
    val totalSize get() = tokenTypes.size

    var comments = i0
    var numComments = 0

    fun addComment(i0: Int, i1: Int) {
        if (numComments * 2 >= comments.size) {
            val newSize = max(numComments * 2, 16)
            comments = comments.copyOf(newSize * 2)
        }

        val i = (numComments++) * 2
        comments[i] = i0
        comments[i + 1] = i1
    }

    inline fun <R> push(
        i: Int, open: TokenType, close: TokenType,
        readImpl: () -> R
    ): R {
        val end = findBlockEnd(i, open, close)
        return push(end, readImpl)
    }

    inline fun <R> push(newSize: Int, readImpl: () -> R): R {
        val oldSize = size
        try {
            size = newSize
            return readImpl()
        } finally {
            size = oldSize
        }
    }

    fun <R> push(
        i: Int, openStr: String, closeStr: String,
        readImpl: () -> R
    ): R = push(findBlockEnd(i, openStr, closeStr), readImpl)

    fun findBlockEnd(i: Int, open: TokenType, close: TokenType): Int {
        // todo we could easily pre-compute this in O(n)... does it matter??
        check(equals(i, open)) { "Expected $open, got ${err(i)}" }
        var depth = 1
        var j = i + 1
        while (depth > 0) {
            if (j >= size) {
                printTokensInBlocks(i, open, close)
                LOGGER.warn("Could not find block end for $open/$close at ${err(i)}, #${size - i}")
                return size
            }
            when (getType(j++)) {
                open, TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK, TokenType.INDENT -> depth++
                close, TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK, TokenType.DEDENT -> depth--
                else -> {}
            }
        }
        return j - 1
    }

    fun printTokensInBlocks(i: Int) {
        printTokensInBlocks(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
    }

    fun printTokensInBlocks(i: Int, open: TokenType, close: TokenType) {
        var depth = 0
        for (j in i until size) {
            when (getType(j)) {
                close, TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> {}
            }
            if (depth < 0) break
            LOGGER.info("  ".repeat(depth) + "$j: ${getType(j)} '${toString(j)}'")
            when (getType(j)) {
                open, TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                else -> {}
            }
        }
    }

    fun findBlockEnd(i: Int, open: String, close: String): Int {
        check(equals(i, open))
        var depth = 1
        var j = i + 1
        while (depth > 0) {
            if (equals(j, open)) depth++
            else if (equals(j, close)) depth--
            else when (getType(j)) {
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                else -> {}
            }
            j++
        }
        return j - 1
    }

    fun getType(i: Int): TokenType {
        if (i >= size) throw IndexOutOfBoundsException("$i >= $size at ${err(size - 1)}")
        return TokenType.entries[tokenTypes[i].toInt()]
    }

    fun getTypeUnsafe(i: Int): TokenType {
        return TokenType.entries[tokenTypes[i].toInt()]
    }

    fun setType(i: Int, keyword: TokenType) {
        tokenTypes[i] = keyword.ordinal.toByte()
    }

    fun err(startI: Int, endI: Int = startI): String {
        val startI = clamp(startI, 0, size - 1)
        val endI = clamp(endI, 0, size - 1)

        val ix = getI0(startI)
        val lineNumber = countLines(0, ix)
        val lastLineBreak = source.lastIndexOf('\n', ix)
        val i0 = ix - lastLineBreak
        val i1 = getI1(endI) - lastLineBreak
        // show position in line, and surroundings with arrow ^^^^ for the respective token
        return "${lineNumberStr(lineNumber)}, " +
                style("${i0}-${i1}", StringStyles.TEXT) +
                if (startI == endI) {
                    val type = getTypeUnsafe(startI)
                    ", ${style(type.toString(), type.style)}" +
                            ", '${style(toStringUnsafe(startI), type.style)}'\n"
                } else {
                    "\n"
                } +
                getLinePosString(startI, i0, i1, lastLineBreak)
    }

    fun errShort(i: Int): String {
        val i = clamp(i, 0, size - 1)
        val lineNumber = countLines(0, getI0(i))
        return lineNumberStr(lineNumber)
    }

    fun lineNumberStr(lineNumber: Int): String {
        return "${style(fileName, StringStyles.UNDERLINE + StringStyles.LINK)}:$lineNumber"
    }

    @Suppress("SameParameterValue")
    private fun countLines(i0: Int, i1: Int): Int {
        var count = 1
        for (i in i0 until i1) {
            if (source[i] == '\n') count++
        }
        return count
    }

    private fun CharSequence.isAllWhitespace(i0: Int, i1: Int): Boolean {
        for (j in i0 until i1) {
            if (!this[j].isWhitespace()) return false
        }
        return true
    }

    private fun getLinePosString(tokenIndex0: Int, i0: Int, i1: Int, lastLineBreak: Int): String {
        var start = source.lastIndexOf('\n', max(lastLineBreak - 1, 0)) + 1
        val isSingleLine = source.isAllWhitespace(start, max(lastLineBreak, 0))
        if (isSingleLine) start = lastLineBreak + 1

        var end = source.indexOf('\n', i1 + lastLineBreak)
        if (end < 0) end = source.length
        val tokenLength = max(i1 - i0, 1)

        if (isSingleLine) {
            var i0 = i0
            while (source[start].isWhitespace()) {
                start++
                i0--
            }

            return "${buildStyledSubString(tokenIndex0, start, end)}\n" +
                    " ".repeat(i0 - 1) +
                    style("^".repeat(tokenLength), StringStyles.YELLOW)
        }

        // todo nicely formatted line numbers before it
        // todo trim the lines
        return "${buildStyledSubString(tokenIndex0, start, end)}\n" +
                " ".repeat(i0 - 1) +
                style("^".repeat(tokenLength), StringStyles.YELLOW)
    }

    private fun buildStyledSubString(tokenIndex0: Int, si0: Int, si1: Int): CharSequence {

        var tokenIndex = tokenIndex0
        while (tokenIndex > 0 && getI1(tokenIndex - 1) >= si0) tokenIndex--

        val result = StringBuilder()
        var sourceIndex = si0
        while (sourceIndex < si1) {
            while (tokenIndex < totalSize &&
                (getI1(tokenIndex) <= sourceIndex ||
                        // we can also increment, if the current style is blank, or the token is empty
                        getI0(tokenIndex) >= getI1(tokenIndex) ||
                        getTypeUnsafe(tokenIndex).style.isEmpty())
            ) tokenIndex++

            if (tokenIndex >= totalSize) {// last token reached
                result.append(source, sourceIndex, si1)
                break
            }

            val i0 = max(getI0(tokenIndex), sourceIndex)
            val i1 = min(getI1(tokenIndex), si1)

            val beforeI = min(i0, si1)

            // append non-styled
            result.append(source, sourceIndex, beforeI)
            sourceIndex = beforeI

            if (i1 > sourceIndex) {
                // append styled
                result.append(getTypeUnsafe(tokenIndex).style)
                result.append(source, sourceIndex, i1)
                result.append(StringStyles.RESET)
                sourceIndex = i1
            }
        }

        return result
    }

    fun getI0(i: Int) = offsets[i * 2]
    fun getI1(i: Int) = offsets[i * 2 + 1]
    fun setI1(i: Int, value: Int) {
        offsets[i * 2 + 1] = value
    }

    fun add(type: TokenType, i0: Int, i1: Int) {
        if (size == tokenTypes.size) {
            // resizing
            tokenTypes = tokenTypes.copyOf(size * 2)
            offsets = offsets.copyOf(size * 4)
        }

        if (i0 > i1) throw IllegalStateException("i0 > i1, $i0 > $i1 in $fileName")
        if (i1 > source.length) throw IllegalStateException("i1 > src.len, $i1 > ${source.length} in $fileName")

        if (size > 0 && type == TokenType.SYMBOL &&
            getType(size - 1) == TokenType.SYMBOL &&
            i0 == offsets[size * 2 - 1] &&
            source[i0] != ';' &&
            source[i0 - 1] != ';' &&
            (source[i0] != '>' || source[i0 - 1] == '-') && // ?>
            (source[i0 - 1] != '>' || source[i0] == '=') && // >?, >>, >>., but allow >=
            !(source[i0 - 1] == '*' && source[i0] == '>') && // <*>
            !(source[i0 - 1] == '<' && source[i0] in "*?@") && // <*>, <?, <@
            !(source[i0 - 1] == '!' && source[i0] in ":.") && // !!::, !!.
            !(source[i0 - 1] == '.' && source[i0] in "+-<") && // ..+3.0, ..-3.0
            !(source[i0 - 1] in "&|" && source[i0] == '!') && // &!, |!
            (source[i0 - 1] != '=' || source[i0] == '=')
        ) {
            // todo only accept a symbol if the previous is not =, or the current one is =, too
            // extend symbol
            offsets[size * 2 - 1] = i1
        } else {
            tokenTypes[size] = type.ordinal.toByte()
            offsets[size * 2] = i0
            offsets[size * 2 + 1] = i1
            size++
        }
    }

    fun equals(i: Int, type: TokenType): Boolean {
        return i in 0 until size && getType(i) == type
    }

    fun equals(i: Int, type: TokenType, type2: TokenType): Boolean {
        return i in 0 until size && run {
            val t = getType(i)
            t == type || t == type2
        }
    }

    fun equals(i: Int, type: TokenType, type2: TokenType, type3: TokenType): Boolean {
        return i in 0 until size && run {
            val t = getType(i)
            t == type || t == type2 || t == type3
        }
    }

    fun equals(i: Int, str: String): Boolean {
        if (i !in 0 until size) return false
        if (equals(i, TokenType.STRING)) return false
        val i0 = getI0(i)
        val i1 = getI1(i)
        if (i1 - i0 != str.length) return false
        return source.regionMatches(i0, str, 0, str.length, false)
    }

    fun equals(i: Int, str: String, str2: String): Boolean {
        return equals(i, str) || equals(i, str2)
    }

    fun equals(i: Int, str: String, str2: String, str3: String): Boolean {
        return equals(i, str) || equals(i, str2) || equals(i, str3)
    }

    fun equals(i: Int, vararg strings: String): Boolean {
        return strings.any { str -> equals(i, str) }
    }

    @Suppress("POTENTIALLY_NON_REPORTED_ANNOTATION")
    @Deprecated("Don't use this method")
    override fun equals(other: Any?): Boolean {
        throw IllegalStateException("Incorrect equals used")
    }

    override fun hashCode(): Int {
        var hash = 1
        for (i in 0 until size) {
            hash = hash * 31 + getType(i).hashCode()
        }
        return hash
    }

    override fun toString(): String {
        return List(size) { i ->
            "${getType(i)}(${toString(i)})"
        }.toString()
    }

    fun toString(i: Int): String {
        if (i >= size) throw IndexOutOfBoundsException("$i >= $size, ${TokenType.entries[tokenTypes[i].toInt()]}")
        return source.substring(getI0(i), getI1(i))
    }

    fun unescapeString(i: Int): String {
        if (i >= size) throw IndexOutOfBoundsException("$i >= $size, ${TokenType.entries[tokenTypes[i].toInt()]}")
        val i0 = getI0(i)
        val i1 = getI1(i)
        for (i in i0 until i1) {
            if (source[i] == '\\') {
                // found first violation
                val tmp = StringBuilder(i1 - i0)
                tmp.append(source, i0, i)
                return unescapeStringImpl(i, i1, tmp)
            }
        }
        return source.substring(i0, i1)
    }

    fun unescapeStringImpl(i0: Int, i1: Int, tmp: StringBuilder): String {
        var i = i0
        while (i < i1) {
            val c = source[i++]
            if (c == '\\') {
                when (val ci = source[i++]) {
                    'n', 'N', '\n' -> tmp.append('\n')
                    'r', 'R', '\r' -> tmp.append('\r')
                    't', 'T', '\t' -> tmp.append('\t')
                    'f', 'F', '\u000c' -> tmp.append('\u000c')
                    '"' -> tmp.append('"')
                    '\\' -> tmp.append('\\')
                    '\'' -> tmp.append('\'')
                    '$' -> tmp.append('$')
                    'u', 'U' -> {
                        val number = tmp.substring(i - 1, i + 3).toInt(16)
                        tmp.append(number.toChar())
                    }
                    else -> throw IllegalStateException("Unknown escape sequence \\$ci")
                }
            } else {
                tmp.append(c)
            }
        }
        return tmp.toString()
    }

    fun endsWith(i: Int, ch: Char): Boolean {
        return source[getI1(i) - 1] == ch
    }

    fun toString(i0: Int, i1: Int): String {
        return (i0 until i1).joinToString(", ") { i ->
            "${getType(i)},'${toString(i)}'"
        }
    }

    fun toStringUnsafe(i: Int): String {
        return toStringUnsafe(i, i + 1)
    }

    fun toStringUnsafe(i0: Int, i1: Int): String {
        return source.substring(getI0(i0), getI1(i1 - 1))
    }

    fun isSameLine(tokenI: Int, tokenJ: Int): Boolean {
        val i0 = getI0(tokenI)
        val i1 = getI1(tokenJ)
        for (i in i0 until i1) {
            if (source[i] == '\n') return false
        }
        return true
    }

    fun removeLast() {
        size--
    }

    @Suppress("DuplicatedCode")
    fun findToken(i0: Int, str: String): Int {
        var depth = 0
        for (i in i0 until size) {
            when {
                depth == 0 && equals(i, str) -> return i
                equals(i, TokenType.OPEN_BLOCK) ||
                        equals(i, TokenType.OPEN_ARRAY) ||
                        equals(i, TokenType.OPEN_CALL) -> depth++
                equals(i, TokenType.CLOSE_BLOCK) ||
                        equals(i, TokenType.CLOSE_ARRAY) ||
                        equals(i, TokenType.CLOSE_CALL) -> depth--
            }
        }
        return -1
    }

    fun readPath(i: Int, scopeType: ScopeType?): Pair<Scope, Int> {
        var j = i
        check(equals(j, TokenType.NAME, TokenType.KEYWORD))
        var path = root.getOrPut(toString(j++), scopeType)
        if (scopeType == ScopeType.PACKAGE) {
            path.setEmptyTypeParams()
        }

        while (equals(j, ".") && equals(j + 1, TokenType.NAME, TokenType.KEYWORD)) {
            path = path.getOrPut(toString(j + 1), scopeType)
            if (scopeType == ScopeType.PACKAGE) {
                path.setEmptyTypeParams()
            }

            j += 2 // skip period and name
        }
        return path to j
    }

    fun readImport(i: Int): Pair<Import, Int> {
        var (path, j) = readPath(i, null)
        val allChildren = equals(j, ".*")
        if (allChildren) j++
        val name = if (!allChildren &&
            equals(j, "as") &&
            equals(j + 1, TokenType.NAME)
        ) {
            j++
            toString(j++)
        } else path.name
        return Import(path, allChildren, name) to j
    }

    fun toDebugString(): String =
        (0 until size).joinToString(" ") {
            toString(it)
        }

    fun extractString(i0x: Int, i1x: Int): String {
        val i0 = max(getI0(i0x), 0)
        val i1 = min(getI1(min(i1x, size - 1)), source.length)
        if (i1 <= i0) return ""
        return source.substring(i0, i1)
    }

    fun validateBlocks() {
        val blocks = StringBuilder()
        fun remove(char: Char, i: Int) {
            check(char == blocks.lastOrNull()) {
                "Expected $char, but got ${blocks.lastOrNull()} at ${err(i)}"
            }
            blocks.setLength(blocks.length - 1)
        }

        var indent = 0
        for (i in 0 until totalSize) {
            when (getTypeUnsafe(i)) {
                TokenType.OPEN_CALL -> blocks.append('(')
                TokenType.OPEN_BLOCK -> blocks.append('{')
                TokenType.OPEN_ARRAY -> blocks.append('[')
                TokenType.INDENT -> indent++
                TokenType.CLOSE_CALL -> remove('(', i)
                TokenType.CLOSE_BLOCK -> remove('{', i)
                TokenType.CLOSE_ARRAY -> remove('[', i)
                TokenType.DEDENT -> {
                    indent--
                    check(indent >= 0) {
                        "More dedents than indents at ${err(i)}"
                    }
                }
                else -> {}
            }
        }
        check(blocks.isEmpty()) {
            "Asymmetric blocks, got remainder $blocks"
        }
    }
}