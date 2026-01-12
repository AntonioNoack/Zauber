package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.impl.ClassType

class ArrayToVarargsStar(val value: Expression) : Expression(value.scope, value.origin) {
    override fun clone(scope: Scope) = ArrayToVarargsStar(value.clone(scope))
    override fun toStringImpl(depth: Int): String = "*${value.toStringImpl(depth)}"
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun resolveType(context: ResolutionContext): Type = value.resolveType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val tt = context.targetType
        val newTT = if (tt is ClassType && tt.clazz == ArrayType.clazz && tt.typeParameters != null) {
            tt.typeParameters[0]
        } else null
        return value.hasLambdaOrUnknownGenericsType(context.withTargetType(newTT))
    }

}