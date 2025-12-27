package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.getScope
import me.anno.zauber.types.impl.ClassType

class GetClassFromValueExpression(val type: Expression, origin: Int) : Expression(type.scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(type)
    }

    override fun toString(): String {
        return "($type)::class"
    }

    override fun resolveType(context: ResolutionContext): Type {
        val base = TypeResolution.resolveType(context, type)
        return ClassType(getScope("Class"), listOf(base))
    }

    override fun clone(scope: Scope) = GetClassFromValueExpression(type.clone(scope), origin)

}