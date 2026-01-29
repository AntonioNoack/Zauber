package me.anno.zauber.langserver

import me.anno.zauber.tokenizer.TokenList
import kotlin.math.min

fun encodeTokens(tokens: TokenList): List<Int> {
    val data = mutableListOf<Int>()

    var prevLine = 0
    var prevChar = 0

    var line = 0
    var start = 0
    var pos = 0

    val source = tokens.source
    for (t in 0 until tokens.size) {
        var i0 = tokens.getI0(t)
        val i1 = tokens.getI1(t)

        i0 = min(i0, source.length)
        while (pos < i0) {
            if (source[pos] == '\n') {
                start = 0
                line++
            } else start++
            pos++
        }

        val deltaLine = line - prevLine
        val deltaChar =
            if (deltaLine == 0) start - prevChar
            else start

        data += deltaLine
        data += deltaChar
        data += i1 - i0
        data += tokens.getType(t).ordinal
        data += 0 // modifiers

        prevLine = line
        prevChar = start
    }

    return data
}
