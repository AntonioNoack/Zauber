package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

/**
 * [is, !is]
 * */
class IsInstanceOfExpr(val left: Expression, val right: Type, val negated: Boolean, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    val symbol: String get() = if (negated) "!is" else "is"

    override fun toStringImpl(depth: Int): String {
        return "(${left.toString(depth)})$symbol($right)"
    }

    override fun resolveType(context: ResolutionContext): Type = BooleanType
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // always boolean

    override fun clone(scope: Scope): Expression {
        return IsInstanceOfExpr(left.clone(scope), right, negated, scope, origin)
    }
}