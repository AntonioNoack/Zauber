package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.impl.ClassType

class ArrayToVarargsStar(val base: Expression) : Expression(base.scope, base.origin) {
    override fun resolveType(context: ResolutionContext): Type = base.resolveType(context)
    override fun clone(scope: Scope) = ArrayToVarargsStar(base.clone(scope))
    override fun toStringImpl(depth: Int): String = "*${base.toStringImpl(depth)}"
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        val tt = context.targetType
        val newTT = if (tt is ClassType && tt.clazz == ArrayType.clazz && tt.typeParameters != null) {
            tt.typeParameters[0]
        } else null
        return base.hasLambdaOrUnknownGenericsType(context.withTargetType(newTT))
    }

}