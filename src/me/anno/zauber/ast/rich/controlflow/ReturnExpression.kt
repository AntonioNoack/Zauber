package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ReturnExpression(value: Expression, label: String?, scope: Scope, origin: Int) :
    ExitExpression(value, label, scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return if (label == null) "return ${value.toString(depth)}"
        else "return@$label ${value.toString(depth)}"
    }

    override fun resolveReturnType(context: ResolutionContext): Type = NothingType
    override fun resolveThrownType(context: ResolutionContext): Type = value.resolveThrownType(context)
    override fun resolveYieldedType(context: ResolutionContext): Type = value.resolveYieldedType(context)

    override fun clone(scope: Scope) = ReturnExpression(value.clone(scope), label, scope, origin)
    override fun splitsScope(): Boolean = false
    override fun resolveImpl(context: ResolutionContext) =
        ReturnExpression(value.resolve(context), label, scope, origin)

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}