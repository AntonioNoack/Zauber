package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.ThrowableType
import me.anno.zauber.types.Types.YieldableType

/**
 * Use this keyword to manually handle Yieldables (Promises).
 * All exceptions are caught. Return values may be available later or never.
 * */
class AsyncExpression(val value: Expression, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "async ${value.toString(depth)}"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        val resolvedValue = value.resolve(context)
        val returnType = resolvedValue.resolveReturnType(context)
        val thrownType = resolvedValue.resolveThrownType(context)
        val yieldedType = resolvedValue.resolveYieldedType(context)
        return YieldableType.withTypeParameters(listOf(returnType, thrownType, yieldedType))
    }

    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // todo maybe it has...
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun clone(scope: Scope) = AsyncExpression(value.clone(scope), scope, origin)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = value.isResolved()
    override fun resolveImpl(context: ResolutionContext) =
        AsyncExpression(value.resolve(context), scope, origin)

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}