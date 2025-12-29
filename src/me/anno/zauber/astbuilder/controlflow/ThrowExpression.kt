package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

// todo we maybe can pack this into an return Err(thrown), and return into return Ok(value)
class ThrowExpression(origin: Int, val thrown: Expression) : Expression(thrown.scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(thrown)
    }

    override fun toString(depth: Int): String {
        return "throw ${thrown.toString(depth - 1)}"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // always Nothing
    override fun clone(scope: Scope) = ThrowExpression(origin, thrown.clone(scope))

}