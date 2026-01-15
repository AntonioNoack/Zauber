package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.getScope
import me.anno.zauber.types.impl.ClassType

class GetClassFromTypeExpression(val type: Type, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${type.toString(depth)}::class"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return ClassType(getScope("KClass", 1), listOf(type))
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = type.isResolved()

    override fun clone(scope: Scope) = GetClassFromTypeExpression(type, scope, origin)
    override fun resolveImpl(context: ResolutionContext): Expression {
        return GetClassFromTypeExpression(type.resolve(), scope, origin)
    }

}