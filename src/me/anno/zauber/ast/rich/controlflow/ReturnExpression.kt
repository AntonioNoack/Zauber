package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ReturnExpression(val value: Expression, val label: String?, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "return ${value.toString(depth)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // type is known: Nothing
    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun clone(scope: Scope) = ReturnExpression(value.clone(scope), label, scope, origin)

}