package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class NamedTypeExpression(
    val type: Type,
    scope: Scope, origin: Int
) : Expression(scope, origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = type.toString()
    override fun clone(scope: Scope) = NamedTypeExpression(type, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false
    override fun resolveType(context: ResolutionContext): Type = type
}