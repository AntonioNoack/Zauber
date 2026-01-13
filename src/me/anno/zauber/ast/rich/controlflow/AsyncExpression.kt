package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.PromiseType

/**
 * Use this keyword to manually handle Promises
 * */
class AsyncExpression(origin: Int, val value: Expression) : Expression(value.scope, origin) {
    override fun toStringImpl(depth: Int): String {
        return "async ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = PromiseType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // todo maybe it has...
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun clone(scope: Scope) = AsyncExpression(origin, value.clone(scope))
    override fun splitsScope(): Boolean = false
}