package me.anno.zauber.ast.rich.parser

import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.utils.NumberUtils.pack64
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.impl.GenericType

open class ASTBuilderBase(val tokens: TokenList, val root: Scope, val language: Language) {

    companion object {
        private val LOGGER = LogManager.getLogger(ASTBuilderBase::class)

        val shouldBeResolvable = emptyList<Import>()
    }

    val semantic
        get() = tokens.semantic

    var flags = 0

    var currPackage = root
    var i = 0

    fun packFlags(): FlagSet {
        val result = flags
        flags = 0
        return result
    }

    fun addFlag(flag: FlagSet) {
        flags = flags or flag
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

    inline fun <R> pushScope(scope: Scope, callback: (Scope) -> R): R {
        val parent = currPackage
        currPackage = scope
        val value = callback(scope)
        currPackage = parent
        return value
    }

    fun origin(i: Int): Long {
        val ptr = TokenListIndex.getIndex(tokens, i)
        return pack64(ptr, ptr)
    }

    inline fun <R> pushBlockLike(
        readImpl: () -> R,
        open: TokenType, close: TokenType
    ): R {
        // val size0 = tokens.size
        val end = tokens.findBlockEnd(i, open, close)
        consume(open)
        val result = tokens.push(end, readImpl)
        check(i == end) { "Missed reading tokens, $i != $end, ${tokens.err(i)}" }
        // println("pushBlock, $i vs $size0 vs ${tokens.size}")
        consume(close)
        return result
    }

    inline fun <R> pushCall(readImpl: () -> R): R {
        return pushBlockLike(readImpl, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
    }

    inline fun <R> pushBlock(readImpl: () -> R): R {
        return pushBlockLike(readImpl, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
    }

    inline fun <R> pushArray(readImpl: () -> R): R {
        return pushBlockLike(readImpl, TokenType.OPEN_ARRAY, TokenType.CLOSE_ARRAY)
    }

    fun skipCall() {
        // val size0 = tokens.size
        val end = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
        consume(TokenType.OPEN_CALL)
        i = end
        consume(TokenType.CLOSE_CALL)
    }

    fun readComma() {
        if (tokens.equals(i, TokenType.COMMA)) i++
        else if (i < tokens.size) throw IllegalStateException("Expected comma, but got $i<${tokens.size} ${tokens.err(i)}")
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

    fun consume(expected: String, expectedType: TokenType) {
        consume(expected); i--
        consume(expectedType)
    }

    fun consumeIf(keyword: String): Boolean {
        return if (tokens.equals(i, keyword)) {
            i++
            true
        } else false
    }

    fun consumeIf(keyword: String, vsCodeType: VSCodeType, modifiers: Int): Boolean {
        return if (tokens.equals(i, keyword)) {
            semantic?.setLSType(i, vsCodeType, modifiers)
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
    val generics get() = genericParams.last()

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

    fun resolveJumpLabel(label: String?): Scope {
        var scope = currPackage
        if (label == null) {
            while (true) {
                if (scope.jumpLabel != null) return scope
                scope = scope.parentIfSameFile
                    ?: throw IllegalStateException(
                        "Could not resolve jump label $label@ " +
                                "in $currPackage " +
                                "at ${tokens.err(i)}, " +
                                "exit at ${scope.pathStr}"
                    )
            }
        } else {
            while (true) {
                if (scope.jumpLabel == label) return scope
                scope = scope.parentIfSameFile
                    ?: throw IllegalStateException(
                        "Could not resolve jump label $label@ " +
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
            if (scope.isClassLike() && (scope.name == label || label == null)) {
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

    fun resolveSuperLabel(label: String?): Scope {
        var scope = currPackage
        while (true) {
            // check if this is an instance
            if (scope.isClassLike()) {
                return resolveSuperLabelI(label, scope)
            }

            scope = scope.parentIfSameFile
                ?: throw IllegalStateException("Could not resolve super-label@$label in $currPackage")
        }
    }

    fun resolveSuperLabelI(label: String?, scope: Scope): Scope {
        check(scope.isClassLike())
        val parents = scope.superCalls
        if (label == null) {
            // todo this should be smarter w.r.t. when we access a child member,
            //  and we have multiple candidates (class and interfaces or multiple interfaces)
            //  we can at least check whether the member exists in the super class, and prefer that one
            if (parents.isEmpty()) throw IllegalStateException("Cannot access super in $scope")
            if (parents.size == 1) return parents[0].type.clazz

            val uniqueScopes = parents.map { it.type.clazz }
                .distinct()

            if (uniqueScopes.size == 1) {
                return uniqueScopes[0]
            }

            // todo we may need to support generics, when we allow inheriting from interfaces with different generics,
            //  e.g. class X: Function<Int>, Function<Float>
            LOGGER.warn("Super without label is ambiguous for $scope, selecting ${uniqueScopes[0]}")
            return uniqueScopes[0]
        } else {
            return resolveSuperLabelByName(label, scope)
                ?: throw IllegalStateException("Missing super type called '$label' in $scope")
        }
    }

    fun resolveSuperLabelByName(label: String, scope: Scope): Scope? {
        check(scope.isClassLike())
        val parents = scope.superCalls
        for (parent in parents) {
            if (parent.type.clazz.name == label) {
                return parent.type.clazz
            }
        }
        for (parent in parents) {
            val solution = resolveSuperLabelByName(label, parent.type.clazz)
            if (solution != null) return solution
        }
        return null
    }
}