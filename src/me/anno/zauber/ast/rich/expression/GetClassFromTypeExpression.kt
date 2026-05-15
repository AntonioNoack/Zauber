package me.anno.zauber.ast.rich.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.arithmetic.UnionType

class GetClassFromTypeExpression(val type: Type, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${type.toString(depth)}::class"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        return when (type) {
            is UnionType -> Types.UnionType
            is ClassType -> Types.ClassType.withTypeParameter(type)
            else -> Types.TypeT
        }
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = type.isResolved()

    override fun clone(scope: Scope) = GetClassFromTypeExpression(type, scope, origin)
    override fun resolveImpl(context: ResolutionContext): Expression {
        return GetClassFromTypeExpression(type.resolve(), scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {}

}