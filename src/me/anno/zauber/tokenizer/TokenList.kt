package me.anno.zauber.tokenizer

import me.anno.zauber.Compile.root
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import kotlin.math.max

class TokenList(val source: CharSequence, val fileName: String) {

    companion object {
        private val LOGGER = LogManager.getLogger(TokenList::class)
        private val i0 = IntArray(0)
    }

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
                open, TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                close, TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
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

    fun err(i: Int): String {
        val i = max(i, 0)
        val before = source.substring(0, getI0(i))
        val lineNumber = before.count { it == '\n' } + 1
        val lastLineBreak = before.lastIndexOf('\n')
        val i0 = getI0(i) - lastLineBreak
        val i1 = getI1(i) - lastLineBreak
        // show position in line, and surroundings with arrow ^^^^ for the respective token
        val posStr = getLinePosString(i0, i1, before, lineNumber, lastLineBreak)
        return "$fileName:$lineNumber, ${i0}-${i1}, ${getTypeUnsafe(i)}, '${toStringUnsafe(i)}'\n$posStr"
    }

    fun getLinePosString(i0: Int, i1: Int, before: String, lineNumber: Int, lastLineBreak: Int): String {
        var start = before.lastIndexOf('\n', lastLineBreak - 1) + 1
        val isSingleLine = before.substring(start, lastLineBreak).all { it.isWhitespace() }
        if (isSingleLine) start = lastLineBreak + 1

        var end = source.indexOf('\n', i1 + lastLineBreak)
        if (end < 0) end = source.length
        val tokenLength = i1 - i0

        if (isSingleLine) {
            var i0 = i0
            while (source[start].isWhitespace()) {
                start++
                i0--
            }

            val lnPrefix = "$fileName:$lineNumber | "
            return "$lnPrefix${source.substring(start, end)}\n" +
                    " ".repeat(i0 + lnPrefix.length - 1) +
                    "^".repeat(tokenLength)
        }

        // todo nicely formatted line numbers before it
        // todo trim the lines
        return "${source.substring(start, end)}\n" +
                " ".repeat(i0 - 1) +
                "^".repeat(tokenLength)
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
        return str.indices.all { strIndex ->
            str[strIndex] == source[i0 + strIndex]
        }
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

    fun endsWith(i: Int, ch: Char): Boolean {
        return source[getI1(i) - 1] == ch
    }

    fun toString(i0: Int, i1: Int): String {
        return (i0 until i1).joinToString(", ") { i ->
            "${getType(i)},'${toString(i)}'"
        }
    }

    fun toStringUnsafe(i: Int): String {
        return source.substring(getI0(i), getI1(i))
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

    fun readPath(i: Int): Pair<Scope, Int> {
        var j = i
        check(equals(j, TokenType.NAME, TokenType.KEYWORD))
        var path = root.getOrPut(toString(j++), null)
        while (equals(j, ".") && equals(j + 1, TokenType.NAME, TokenType.KEYWORD)) {
            path = path.getOrPut(toString(j + 1), null)
            j += 2 // skip period and name
        }
        return path to j
    }

    fun readImport(i: Int): Pair<Import, Int> {
        var (path, j) = readPath(i)
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
}