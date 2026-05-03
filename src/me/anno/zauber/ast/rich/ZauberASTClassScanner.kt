package me.anno.zauber.ast.rich

import me.anno.support.Language
import me.anno.zauber.tokenizer.TokenList

/**
 * to make type-resolution immediately available/resolvable
 * */
class ZauberASTClassScanner(tokens: TokenList, language: Language = Language.ZAUBER) :
    ASTClassScanner(tokens, language) {

    companion object {
        fun scanAllClasses(allTokens: List<TokenList>) {
            for (i in allTokens.indices) {
                val tokens = allTokens[i]
                scanClasses(tokens)
            }
        }

        fun scanClasses(tokens: TokenList, language: Language = Language.byFileName(tokens.fileName)) {
            ZauberASTClassScanner(tokens, language).readFileLevel()
        }
    }
}