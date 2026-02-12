package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class ArrayToVarargsStar(val value: Expression) : Expression(value.scope, value.origin) {
    override fun clone(scope: Scope) = ArrayToVarargsStar(value.clone(scope))
    override fun toStringImpl(depth: Int): String = "*${value.toStringImpl(depth)}"
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun resolveReturnType(context: ResolutionContext): Type = value.resolveReturnType(context)
    override fun splitsScope(): Boolean = value.splitsScope()
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val tt = context.targetType
        val newTT = if (tt is ClassType && tt.clazz == Types.ArrayType.clazz && tt.typeParameters != null) {
            tt.typeParameters[0]
        } else null
        return value.hasLambdaOrUnknownGenericsType(context.withTargetType(newTT))
    }
    override fun isResolved(): Boolean = false
    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}