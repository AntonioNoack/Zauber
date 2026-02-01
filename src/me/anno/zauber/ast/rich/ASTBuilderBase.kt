package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.impl.GenericType

open class ASTBuilderBase(val tokens: TokenList, val root: Scope) {

    var keywords = 0

    var currPackage = root
    var i = 0

    fun packKeywords(): KeywordSet {
        val result = keywords
        keywords = 0
        return result
    }

    inline fun <R> pushScope(scopeType: ScopeType, prefix: String, callback: (Scope) -> R): R {
        val name = currPackage.generateName(prefix)
        return pushScope(name, scopeType, callback)
    }

    inline fun <R> pushScope(name: String, scopeType: ScopeType, callback: (Scope) -> R): R {
        val parent = currPackage
        val child = parent.getOrPut(name, scopeType)
        currPackage = child
        val value = callback(child)
        currPackage = parent
        return value
    }

    inline fun <R> pushScope(scope: Scope, callback: () -> R): R {
        val parent = currPackage
        currPackage = scope
        val value = callback()
        currPackage = parent
        return value
    }

    fun origin(i: Int): Int {
        return TokenListIndex.getIndex(tokens, i)
    }

    inline fun <R> pushCall(readImpl: () -> R): R {
        val size0 = tokens.size
        val end = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
        consume(TokenType.OPEN_CALL)
        val result = tokens.push(end, readImpl)
        check(i == end) { "Missed reading tokens, $i != $end, ${tokens.err(i)}" }
        println("pushCall, $i vs $size0 vs ${tokens.size}")
        consume(TokenType.CLOSE_CALL)
        return result
    }

    inline fun <R> pushArray(readImpl: () -> R): R {
        val end = tokens.findBlockEnd(i, TokenType.OPEN_ARRAY, TokenType.CLOSE_ARRAY)
        consume(TokenType.OPEN_ARRAY)
        val result = tokens.push(end, readImpl)
        check(i == end) { "Missed reading tokens, $i != $end, ${tokens.err(i)}" }
        consume(TokenType.CLOSE_ARRAY)
        return result
    }

    fun readComma() {
        if (tokens.equals(i, TokenType.COMMA)) i++
        else if (i < tokens.size) throw IllegalStateException("Expected comma, but got ${tokens.err(i)}")
    }

    fun consume(expected: String) {
        check(tokens.equals(i, expected) && !tokens.equals(i, TokenType.STRING)) {
            "Expected '$expected', but found ${tokens.err(i)}"
        }
        i++
    }

    fun consume(expected: TokenType) {
        check(tokens.equals(i, expected)) {
            "Expected '$expected', but found ${tokens.err(i)}"
        }
        i++
    }

    fun consumeIf(string: String): Boolean {
        return if (tokens.equals(i, string) && !tokens.equals(i, TokenType.STRING)) {
            i++
            true
        } else false
    }

    fun consumeIf(string: String, vsCodeType: VSCodeType, modifiers: Int): Boolean {
        return if (tokens.equals(i, string) && !tokens.equals(i, TokenType.STRING)) {
            if (this is ZauberASTBuilder) {
                setLSType(i, vsCodeType, modifiers)
            }
            i++
            true
        } else false
    }

    fun consumeIf(type: TokenType): Boolean {
        return if (tokens.equals(i, type)) {
            i++
            true
        } else false
    }

    val imports = ArrayList<Import>()

    val genericParams = ArrayList<HashMap<String, GenericType>>()

    init {
        genericParams.add(HashMap())
    }

    fun pushGenericParams() {
        genericParams.add(HashMap(genericParams.last()))
    }

    fun popGenericParams() {
        @Suppress("Since15")
        genericParams.removeLast()
    }

    fun nameAsImport(name: String): List<Import> {
        return imports.filter { it.name == name }
    }

    val shouldBeResolvable = emptyList<Import>()

    fun resolveBreakLabel(label: String?): Scope {
        var scope = currPackage
        if (label == null) {
            while (true) {
                if (scope.breakLabel != null) return scope
                scope = scope.parentIfSameFile
                    ?: throw IllegalStateException(
                        "Could not resolve break-label@$label " +
                                "in $currPackage " +
                                "at ${tokens.err(i)}, " +
                                "exit at ${scope.pathStr}"
                    )
            }
        } else {
            while (true) {
                if (scope.breakLabel == label) return scope
                scope = scope.parentIfSameFile
                    ?: throw IllegalStateException(
                        "Could not resolve break-label@$label " +
                                "in $currPackage " +
                                "at ${tokens.err(i)}, " +
                                "exit at ${scope.pathStr}"
                    )
            }
        }
    }

    fun resolveThisLabel(label: String?): Scope {
        var scope = currPackage
        while (true) {
            // check if this is an instance
            if (scope.isClassType() && (scope.name == label || label == null)) {
                return scope
            }

            val method = scope.selfAsMethod
            if (method != null && method.explicitSelfType) {
                if (method.name == label || label == null) {
                    return scope
                }
            }

            val constructor = scope.selfAsConstructor
            if (constructor != null && (label == null || label == "constructor")) {
                return scope
            }

            scope = scope.parentIfSameFile
                ?: throw IllegalStateException("Could not resolve this-label@$label in $currPackage")
        }
    }
}