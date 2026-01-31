package me.anno.zauber.langserver

import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.rich.ZauberASTBuilder
import kotlin.math.min

class VSTokenEncoder(private val builder: ZauberASTBuilder) {
    private val tokens = builder.tokens
    private val data = ArrayList<Int>((tokens.size + tokens.numComments) * 5)

    private var prevLine = 0
    private var prevChar = 0

    private var line = 0
    private var start = 0
    private var pos = 0

    private fun skipTo(i0: Int) {
        val source = tokens.source
        val i0 = min(i0, source.length)
        while (pos < i0) {
            if (source[pos] == '\n') {
                start = 0
                line++
            } else start++
            pos++
        }
    }

    private fun push(type: Int, modifiers: Int, i0: Int, i1: Int) {
        skipTo(i0)

        val deltaLine = line - prevLine
        val deltaChar =
            if (deltaLine == 0) start - prevChar
            else start

        data += deltaLine
        data += deltaChar
        data += i1 - i0
        data += type
        data += modifiers

        prevLine = line
        prevChar = start
    }

    private var nextComment = 0
    private fun pushCommentsUntil(maxI: Int) {
        while (nextComment < tokens.numComments) {
            val i0 = tokens.comments[nextComment * 2]
            val i1 = tokens.comments[nextComment * 2 + 1]
            if (i1 > maxI) return

            push(VSCodeType.COMMENT.ordinal, 0, i0, i1)
            nextComment++
        }
    }

    // the output tokens must be sorted to be valid
    fun encodeTokens(): List<Int> {
        for (t in 0 until tokens.size) {
            val type = builder.lsTypes[t]
            if (type == -1) continue // skip

            val i0 = tokens.getI0(t)
            val i1 = tokens.getI1(t)
            pushCommentsUntil(i0)
            push(type, builder.lsModifiers[t], i0, i1)
        }
        pushCommentsUntil(tokens.size)
        return data
    }
}
