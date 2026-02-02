package me.anno.support.java.ast

import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.ASTClassScanner
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.NullableAnyType

/**
 * to make type-resolution immediately available/resolvable
 * */
open class JavaASTClassScanner(tokens: TokenList) : ASTClassScanner(tokens) {

    companion object {
        fun collectNamedJavaClasses(tokens: TokenList) {
            JavaASTClassScanner(tokens).readFileLevel()
        }

        fun ASTClassScanner.collectGenericTypes(classScope: Scope): List<Parameter> {
            val genericParams = if (consumeIf("<")) {
                val typeParameters = ArrayList<Parameter>()
                var depth = 1
                var canReadType = true
                while (depth > 0 && i < tokens.size) {
                    if (tokens.equals(i, "<")) depth++
                    else if (tokens.equals(i, ">")) depth--
                    else when (tokens.getType(i)) {
                        TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                        TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                        TokenType.COMMA -> if (depth == 1) canReadType = true
                        else -> if (depth == 1 && canReadType) {
                            // skip annotations
                            skipAnnotations()

                            if (tokens.equals(i, TokenType.NAME)) {
                                val name = tokens.toString(i)
                                val type = NullableAnyType
                                typeParameters.add(Parameter(typeParameters.size, name, type, classScope, i))
                                canReadType = false
                            }
                        }
                    }
                    i++
                }
                typeParameters
            } else emptyList()
            return genericParams
        }

    }

    override fun foundNamedScope(name: String, listenType: KeywordSet, scopeType: ScopeType) {
        val classScope = pushNamedScope(name, listenType, scopeType)
        val genericParams = collectGenericTypes(classScope)

        classScope.typeParameters = genericParams
        classScope.hasTypeParameters = true
        if (false) println("Defined type parameters for ${classScope.pathStr}")

        skipValueParameters()

        if (consumeIf("extends")) {
            collectSuperNames(classScope)
        }

        if (consumeIf("implements")) {
            collectSuperNames(classScope)
        }

        handleClassBody(classScope, scopeType)
    }

    override fun readImport() {
        // skip 'static' import flag
        consumeIf("static")
        super.readImport()
    }

    override fun checkForTypes() {
        val hasName = tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD)
        if (!hasName) return

        var keywords = 0
        val scopeType = when {
            consumeIf("enum") -> ScopeType.ENUM_CLASS
            consumeIf("record") -> {
                keywords = Keywords.VALUE
                ScopeType.NORMAL_CLASS
            }
            consumeIf("class") -> ScopeType.NORMAL_CLASS
            consumeIf("interface") -> ScopeType.INTERFACE
            else -> return
        }

        val name = consumeName(VSCodeType.TYPE, 0)
        foundNamedScope(name, keywords, scopeType)
    }
}