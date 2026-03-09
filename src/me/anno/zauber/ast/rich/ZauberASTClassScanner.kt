package me.anno.zauber.ast.rich

import me.anno.zauber.tokenizer.TokenList

/**
 * to make type-resolution immediately available/resolvable
 * */
class ZauberASTClassScanner(tokens: TokenList) : ASTClassScanner(tokens) {
    companion object {
        fun scanAllClasses(allTokens: List<TokenList>) {
            for (i in allTokens.indices) {
                val tokens = allTokens[i]
                scanClasses(tokens)
            }
        }

        fun scanClasses(tokens: TokenList) {
            ZauberASTClassScanner(tokens).readFileLevel()
        }
    }
}