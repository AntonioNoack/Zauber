package me.anno.support.cpp.preprocessor

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class TokenBuilder(fileName: String) {

    var depth = 0

    fun addRange(type: TokenType, i0: Int, i1: Int, src: TokenList, lineNumber: Int) {

        // println("Adding $type, '${src.source.substring(i0, i1)}', #$lineNumber")

        if (i0 + 1 == i1 && (type == TokenType.COMMA || type == TokenType.CLOSE_CALL)) {
            source.append(src.source[i0])
            tokens.add(type, source.length - 1, source.length)
        } else {
            beginLine()
            val selfI0 = source.length
            source.append(src.source, i0, i1)
            tokens.add(type, selfI0, selfI0 + i1 - i0)
        }
        lineNumbers.add(src.fileName, lineNumber)

        // todo dedent (safely) before }

        if (type == TokenType.CLOSE_BLOCK) depth--
        endLine()
        if (type == TokenType.OPEN_BLOCK) depth++
    }

    fun addRangePlusQuotes(type: TokenType, i0: Int, i1: Int, src: TokenList, lineNumber: Int) {
        beginLine()
        val selfI0 = source.length
        source.append('"').append(src.source, i0, i1).append('"')
        tokens.add(type, selfI0, source.length)
        lineNumbers.add(src.fileName, lineNumber)
        endLine()
    }

    fun beginLine() {
        if (tokens.size > 0 && tokens.equals(tokens.size - 1, TokenType.NAME, TokenType.KEYWORD)) {
            source.append(' ')
        } else if (source.endsWith(',')) {
            source.append(' ')
        }
    }

    // todo handle indentation depth...

    fun endLine() {
        if (source.last() in ";{}") {
            source.append('\n')
            repeat(depth) { source.append("  ") }
        }
    }

    fun addToken(src: TokenList, tokenIndex: Int) {
        val lineNumber = src.getLineNumber(tokenIndex)
        addRange(src.getType(tokenIndex), src.getI0(tokenIndex), src.getI1(tokenIndex), src, lineNumber)
    }

    val source = StringBuilder()
    val tokens = TokenList(source, fileName)
    val lineNumbers = LineNumbers(true)

    init {
        tokens.lineNumbers = lineNumbers
    }
}