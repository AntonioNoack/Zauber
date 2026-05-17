package me.anno.zauber.ast.rich.parser

import me.anno.langserver.VSCodeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class SemanticTokenList(tokens: TokenList) {

    // todo now that we use lazy-parsing, we should but these into ASTClassScanner...
    val types = ByteArray(tokens.totalSize)
    val modifiers = IntArray(tokens.totalSize)

    init {
        types.fill(-1)
    }

    fun setLSType(i: Int, type: VSCodeType, modifiers: Int) {
        types[i] = type.ordinal.toByte()
        this.modifiers[i] = modifiers
    }

    init {
        // numbers and strings are trivial to fill
        val tmp = tokens.size
        tokens.size = tokens.totalSize
        for (i in 0 until tokens.totalSize) {
            types[i] = when (tokens.getType(i)) {
                TokenType.NUMBER -> VSCodeType.NUMBER
                TokenType.STRING -> VSCodeType.STRING
                TokenType.SYMBOL -> VSCodeType.OPERATOR
                TokenType.KEYWORD -> VSCodeType.KEYWORD
                else -> continue
            }.ordinal.toByte()
        }
        tokens.size = tmp
    }

}