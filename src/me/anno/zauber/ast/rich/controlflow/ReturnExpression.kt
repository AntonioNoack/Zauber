package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ReturnExpression(val value: Expression, val label: String?, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return if (label == null) "return ${value.toString(depth)}"
        else "return@$label ${value.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // type is known: Nothing
    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun clone(scope: Scope) = ReturnExpression(value.clone(scope), label, scope, origin)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = value.isResolved()

    override fun resolve(context: ResolutionContext): Expression {
        return ReturnExpression(value.resolve(context), label, scope, origin)
    }
}