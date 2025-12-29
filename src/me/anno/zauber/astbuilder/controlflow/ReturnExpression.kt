package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ReturnExpression(val value: Expression?, val label: String?, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        if (value != null) callback(value)
    }

    override fun toStringImpl(depth: Int): String {
        return if (value == null) "return"
        else "return ${value.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // type is known: Nothing
    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun clone(scope: Scope) = ReturnExpression(value?.clone(scope), label, scope, origin)

}