package me.anno.support.cpp.preprocessor

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class TokenBuilder(fileName: String) {
    fun addRange(type: TokenType, i0: Int, i1: Int, src: TokenList) {
        val selfI0 = source.length
        source.append(src.source, i0, i1)
        tokens.add(type, selfI0, selfI0 + i1 - i0)
    }

    fun addRangePlusQuotes(type: TokenType, i0: Int, i1: Int, src: TokenList) {
        val selfI0 = source.length
        source.append('"').append(src.source, i0, i1).append('"')
        tokens.add(type, selfI0, source.length)
        // println("Added in quotes: ${tokens.err(tokens.size-1)}")
    }

    fun addToken(src: TokenList, tokenIndex: Int) {
        addRange(src.getType(tokenIndex), src.getI0(tokenIndex), src.getI1(tokenIndex), src)
    }

    val source = StringBuilder()
    val tokens = TokenList(source, fileName)
}