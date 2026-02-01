package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedSetFieldExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * this.name [+=, *=, /=, ...] right
 * */
class AssignIfMutableExpr(
    val left: Expression, val symbol: String,
    val plusImports: List<Import>,
    val plusAssignImports: List<Import>,
    val right: Expression,
) : Expression(left.scope, right.origin) {

    companion object {
        fun plusName(symbol: String) = when (symbol) {
            "+", "+=" -> "plus"
            "-", "-=" -> "minus"
            "*", "*=" -> "times"
            "/", "/=" -> "div"
            "%", "%=" -> "rem"
            "&", "&=" -> "and"
            "^", "^=" -> "xor"
            "|", "|=" -> "or"
            "<<", "<<=" -> "shl"
            ">>", ">>=" -> "shr"
            ">>>", ">>>=" -> "ushr"
            else -> throw Exception("Unknown symbol $symbol")
        }

        fun plusAssignName(symbol: String) = when (symbol) {
            "+", "+=" -> "plusAssign"
            "-", "-=" -> "minusAssign"
            "*", "*=" -> "timesAssign"
            "/", "/=" -> "divAssign"
            "%", "%=" -> "remAssign"
            "&", "&=" -> "andAssign"
            "^", "^=" -> "xorAssign"
            "|", "|=" -> "orAssign"
            "<<", "<<=" -> "shlAssign"
            ">>", ">>=" -> "shrAssign"
            ">>>", ">>>=" -> "ushrAssign"
            else -> throw Exception("Unknown symbol $symbol")
        }
    }

    private val plusName get() = plusName(symbol)
    private val plusAssignName get() = plusAssignName(symbol)

    override fun toStringImpl(depth: Int): String {
        return "${left.toString(depth)} $symbol ${right.toString(depth)}"
    }

    private fun findField(expr: Expression, context: ResolutionContext): ResolvedField? {
        return when (expr) {
            is FieldExpression -> expr.resolveField(context)
            is UnresolvedFieldExpression -> expr.resolveField(context)
            is ResolvedGetFieldExpression -> expr.field
            else -> throw NotImplementedError("Is ${expr.javaClass.simpleName} a mutable left side?")
        }
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return left.needsBackingField(methodScope) ||
                right.needsBackingField(methodScope)
    }

    private fun getMethodOrNull(
        context: ResolutionContext, codeScope: Scope,
        name: String, nameAsImport: List<Import>,
        rightType: Type
    ): ResolvedMethod? {
        return MethodResolver.resolveMethod(
            context, codeScope, name, nameAsImport, null,
            listOf(ValueParameterImpl(null, rightType, false)),
            origin,
        ) as? ResolvedMethod
    }

    fun resolveMethod(context: ResolutionContext): AssignIfMutableResolveResult {
        val field = findField(left, context)
        val isFieldMutable = field?.resolved?.isMutable == true
        val leftType = TypeResolution.resolveType(context, left)
        val rightType = TypeResolution.resolveType(context, right)
        val leftContext = context.withSelfType(leftType)
        val plusMethod = getMethodOrNull(leftContext, scope, plusName, plusImports, rightType)
        val plusAssignMethod = getMethodOrNull(leftContext, scope, plusAssignName, plusAssignImports, rightType)
        check(plusMethod != null || plusAssignMethod != null) {
            "Either a plus or a plusAssign method must be declared on $leftType"
        }

        val isContentMutable = plusAssignMethod != null
        check(isFieldMutable != isContentMutable) {
            "Either field or content must be mutable, $plusName? $plusMethod, $plusAssignName? $plusAssignMethod, fieldMutable? $isFieldMutable"
        }

        return if (isContentMutable) {
            AssignIfMutableResolveResult(leftType, rightType, null, plusAssignMethod)
        } else {
            check(plusMethod != null) { "Cannot resolve $leftType.$plusName($rightType)" }
            AssignIfMutableResolveResult(leftType, rightType, field, plusMethod)
        }
    }

    override fun resolveType(context: ResolutionContext): Type {
        resolveMethod(context) // to crash if something is wrong; not strictly needed
        return exprHasNoType(context)
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // UnitType

    // we don't know better yet
    override fun splitsScope(): Boolean = true

    override fun isResolved(): Boolean = false

    override fun clone(scope: Scope): Expression =
        AssignIfMutableExpr(left.clone(scope), symbol, plusImports, plusAssignImports, right.clone(scope))

    override fun resolveImpl(context: ResolutionContext): Expression {
        val (_, _, dstField, method) = resolveMethod(context)
        val left = left.resolve(context)
        val right = right.resolve(context)
        val call = ResolvedCallExpression(left, method, listOf(right), scope, origin)
        if (dstField != null) {
            check(left is ResolvedGetFieldExpression) {
                "Resolve owner for $this, ${left.javaClass.simpleName}"
            }
            val owner = left.self// ?: dstField.resolveOwnerWithoutLeftSide(origin)
            val setter = ResolvedSetFieldExpression(owner, dstField, call, scope, origin)
            return ExpressionList(listOf(call, setter), scope, origin)
        } else return call
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(left)
        callback(right)
    }
}