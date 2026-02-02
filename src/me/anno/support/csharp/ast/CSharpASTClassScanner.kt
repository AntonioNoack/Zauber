package me.anno.support.csharp.ast

import me.anno.langserver.VSCodeType
import me.anno.support.java.ast.JavaASTClassScanner
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.ScopeType

/**
 * to make type-resolution immediately available/resolvable
 * */
class CSharpASTClassScanner(tokens: TokenList) : JavaASTClassScanner(tokens) {

    companion object {
        fun collectNamedCSharpClasses(tokens: TokenList) {
            CSharpASTClassScanner(tokens).readFileLevel()
        }
    }

    override fun skipAnnotations() {
        while (consumeIf(TokenType.OPEN_ARRAY)) {
            i = tokens.findBlockEnd(i - 1, TokenType.OPEN_ARRAY, TokenType.CLOSE_ARRAY) + 1
        }
    }

    override fun checkImports(): Boolean {
        if (consumeIf("using")) {
            readImport()
            return true// without i++
        } else return false
    }

    override fun checkForTypes() {
        val hasName = tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD)
        if (hasName && consumeIf("struct")) {
            val name = consumeName(VSCodeType.TYPE, 0)
            foundNamedScope(name, keywords, ScopeType.NORMAL_CLASS)
        } else super.checkForTypes()
    }
}