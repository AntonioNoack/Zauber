package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.getScope
import me.anno.zauber.types.impl.ClassType

class GetClassFromTypeExpression(val base: Type, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {}

    override fun toStringImpl(depth: Int): String {
        return "${base.toString(depth)}::class"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return ClassType(getScope("KClass"), listOf(base))
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun clone(scope: Scope) = GetClassFromTypeExpression(base, scope, origin)

}