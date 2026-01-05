package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class IsInstanceOfExpr(val instance: Expression, val type: Type, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    val symbol: String get() = "is"

    override fun toStringImpl(depth: Int): String {
        return "(${instance.toString(depth)})is($type)"
    }

    override fun resolveType(context: ResolutionContext): Type = BooleanType
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // always boolean

    override fun clone(scope: Scope): Expression {
        return IsInstanceOfExpr(instance.clone(scope), type, scope, origin)
    }
}