package me.anno.support.cpp.preprocessor

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class TokenBuilder(fileName: String) {

    fun addRange(type: TokenType, i0: Int, i1: Int, src: TokenList) {
        if (i0 + 1 == i1 && (type == TokenType.COMMA || type == TokenType.CLOSE_CALL)) {
            source.append(src.source[i0])
            tokens.add(type, source.length - 1, source.length)
        } else {
            beginLine()
            val selfI0 = source.length
            source.append(src.source, i0, i1)
            tokens.add(type, selfI0, selfI0 + i1 - i0)
        }
        endLine()
    }

    fun addRangePlusQuotes(type: TokenType, i0: Int, i1: Int, src: TokenList) {
        beginLine()
        val selfI0 = source.length
        source.append('"').append(src.source, i0, i1).append('"')
        tokens.add(type, selfI0, source.length)
        endLine()
    }

    fun beginLine() {
        if (tokens.size > 0 && tokens.equals(tokens.size - 1, TokenType.NAME, TokenType.KEYWORD)) {
            source.append(' ')
        } else if (source.endsWith(',')) {
            source.append(' ')
        }
    }

    fun endLine() {
        // todo handle indentation depth...
        if (source.endsWith(';') || source.endsWith('{')) {
            source.append('\n')
        }
    }

    fun addToken(src: TokenList, tokenIndex: Int) {
        addRange(src.getType(tokenIndex), src.getI0(tokenIndex), src.getI1(tokenIndex), src)
    }

    val source = StringBuilder()
    val tokens = TokenList(source, fileName)
}