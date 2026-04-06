package me.anno.support.java.ast

import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.ASTClassScanner
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

/**
 * to make type-resolution immediately available/resolvable
 * */
open class JavaASTClassScanner(tokens: TokenList) : ASTClassScanner(tokens) {

    companion object {
        fun collectNamedJavaClasses(tokens: TokenList) {
            JavaASTClassScanner(tokens).readFileLevel()
        }
    }

    override fun foundNamedScope(name: String, listenType: FlagSet, scopeType: ScopeType) {
        pushNamedScopeLazy(name, listenType, scopeType) { classScope, readBody ->
            val genericParams = readTypeParameterDeclarations(classScope)

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

            readClassBody(classScope, readBody)
            popGenericParams()
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