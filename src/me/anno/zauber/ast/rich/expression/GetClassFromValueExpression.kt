package me.anno.zauber.ast.rich.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class GetClassFromValueExpression(val value: Expression, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${value.toString(depth)}::class"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        val type = value.resolveReturnType(context)
        return Types.ClassType.withTypeParameter(type)
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = value.splitsScope()
    override fun isResolved(): Boolean = value.isResolved()

    override fun clone(scope: Scope) = GetClassFromValueExpression(value, scope, origin)
    override fun resolveImpl(context: ResolutionContext): Expression {
        return GetClassFromValueExpression(value.resolve(context), scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }

}