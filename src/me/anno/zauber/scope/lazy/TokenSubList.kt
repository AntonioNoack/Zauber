package me.anno.zauber.scope.lazy

import me.anno.zauber.tokenizer.TokenList

class TokenSubList(val tokens: TokenList, val i0: Int, val i1: Int) {
    override fun toString(): String {
        return tokens.toString(i0, i1)
    }

    fun extractString(): String = tokens.extractString(i0, i1 - 1)
}