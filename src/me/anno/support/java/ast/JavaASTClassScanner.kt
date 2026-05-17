package me.anno.support.java.ast

import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.parser.ASTClassScanner
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

/**
 * to make type-resolution immediately available/resolvable
 * */
open class JavaASTClassScanner(tokens: TokenList, language: Language) :
    ASTClassScanner(tokens, language) {

    companion object {
        fun collectNamedJavaClasses(tokens: TokenList) {
            JavaASTClassScanner(tokens, Language.JAVA).readFileLevel()
        }
    }

    override fun foundNamedScope(name: String, listenType: FlagSet, scopeType: ScopeType) {
        pushNamedScopeLazy(name, listenType, scopeType) { classScope, readBody ->
            readTypeParameterDeclarations(classScope, true)

            if (false) println("Defined type parameters for ${classScope.pathStr}")

            skipValueParameters()

            readSuperCalls(classScope, readBody)

            if (consumeIf("permits")) {
                skipSuperNames(classScope)
            }

            readClassBody(classScope, readBody)
            popGenericParams()
        }
    }

    override fun readSuperCalls(classScope: Scope, readBody: Boolean) {
        if (consumeIf("extends")) {
            readSuperCallsImpl(classScope, readBody)
        }

        if (consumeIf("implements")) {
            readSuperCallsImpl(classScope, readBody)
        }
    }

    fun skipSuperNames(classScope: Scope) {
        val name0 = classScope.superCalls.size
        readSuperCalls(classScope, false)
        while (classScope.superCalls.size > name0) {
            @Suppress("Since15")
            classScope.superCalls.removeLast()
        }
    }

    override fun readImport() {
        // skip 'static' import flag
        consumeIf("static")
        super.readImport()
    }

    override fun readNamed() {
        val hasName = tokens.equals(i + 1, TokenType.NAME, TokenType.KEYWORD)
        if (!hasName) return

        var flags = 0
        val scopeType = when {
            consumeIf("enum") -> ScopeType.ENUM_CLASS
            consumeIf("record") -> {
                flags = Flags.VALUE
                ScopeType.NORMAL_CLASS
            }
            consumeIf("class") -> ScopeType.NORMAL_CLASS
            consumeIf("interface") -> ScopeType.INTERFACE
            else -> return
        }

        val name = consumeName(VSCodeType.TYPE, 0)
        foundNamedScope(name, flags, scopeType)
    }
}