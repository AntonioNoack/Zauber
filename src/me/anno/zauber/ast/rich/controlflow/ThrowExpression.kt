package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ThrowExpression(value: Expression, scope: Scope, origin: Int) :
    ExitExpression(value, null, scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "throw ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun clone(scope: Scope) = ThrowExpression(value.clone(scope), scope, origin)
    override fun splitsScope(): Boolean = false
    override fun resolveImpl(context: ResolutionContext) =
        ThrowExpression(value.resolve(context), scope, origin)

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}