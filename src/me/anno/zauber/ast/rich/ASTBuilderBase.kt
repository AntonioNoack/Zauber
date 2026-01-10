package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
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

    fun <R> pushCall(readImpl: () -> R): R {
        val result = tokens.push(i++, TokenType.OPEN_CALL, TokenType.CLOSE_CALL, readImpl)
        consume(TokenType.CLOSE_CALL)
        return result
    }

    fun <R> pushArray(readImpl: () -> R): R {
        val result = tokens.push(i++, TokenType.OPEN_ARRAY, TokenType.CLOSE_ARRAY, readImpl)
        consume("]")
        return result
    }

    fun readComma() {
        if (tokens.equals(i, TokenType.COMMA)) i++
        else if (i < tokens.size) throw IllegalStateException("Expected comma at ${tokens.err(i)}")
    }

    fun consume(expected: String) {
        check(tokens.equals(i, expected)) {
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
        return if (tokens.equals(i, string)) {
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
        genericParams.removeLast()
    }

    fun exprSplitsScope(expr: Expression): Boolean {
        return when (expr) {
            is SpecialValueExpression,
            is StringExpression,
            is NumberExpression,
            is FieldExpression,
            is MemberNameExpression,
            is UnresolvedFieldExpression,
                // todo if-else-branch can enforce a condition: if only one branch returns
            is IfElseBranch,
                // todo while-loop without break can enforce a condition, too
            is WhileLoop, is DoWhileLoop -> false
            is IsInstanceOfExpr -> true // all these (as, as?, is, is?) can change type information...
            is NamedCallExpression -> {
                exprSplitsScope(expr.base) ||
                        expr.valueParameters.any { exprSplitsScope(it.value) }
            }
            is DotExpression -> exprSplitsScope(expr.left) || exprSplitsScope(expr.right)
            is ReturnExpression,
            is ThrowExpression -> false // should these split the scope??? nothing after can happen
            is CallExpression -> {
                exprSplitsScope(expr.base) ||
                        expr.valueParameters.any { exprSplitsScope(it.value) } ||
                        (expr.base is MemberNameExpression && expr.base.name == "check") // this check is a little too loose
            }
            is AssignmentExpression -> true // explicit yes
            is AssignIfMutableExpr -> true // we don't know better yet
            is ExpressionList -> expr.list.any { exprSplitsScope(it) }
            is CompareOp -> exprSplitsScope(expr.value)
            is ImportedMember -> false // I guess not...
            is LambdaExpression -> false // I don't think so
            is BreakExpression, is ContinueExpression -> false // execution ends here anyway
            is CheckEqualsOp -> exprSplitsScope(expr.left) || exprSplitsScope(expr.right)
            is DoubleColonPrefix -> false // some lambda -> no
            is NamedTypeExpression -> false
            is TryCatchBlock -> false // already a split on its own, or is it?
            else -> throw NotImplementedError("Does '$expr' (${expr.javaClass.simpleName}) split the scope (assignment / Nothing-call / ")
        }
    }


}