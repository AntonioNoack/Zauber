package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

// todo we maybe can pack this into an return Err(thrown), and return into return Ok(value)
class ThrowExpression(origin: Int, val value: Expression) : Expression(value.scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "throw ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // always Nothing
    override fun clone(scope: Scope) = ThrowExpression(origin, value.clone(scope))

}