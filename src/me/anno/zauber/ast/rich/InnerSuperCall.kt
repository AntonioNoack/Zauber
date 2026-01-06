package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.ConstructorExpression
import me.anno.zauber.types.Scope

enum class InnerSuperCallTarget {
    THIS,
    SUPER
}

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>,
    val classScope: Scope,
    val origin: Int
) {
    fun toExpr(): ConstructorExpression {
        val clazz = if (target == InnerSuperCallTarget.THIS) classScope else classScope.superCalls[0].type.clazz
        check(clazz.scopeType?.isClassType() == true)
        return ConstructorExpression(
            clazz, emptyList(), valueParameters,
            target == InnerSuperCallTarget.THIS, classScope, origin
        )
    }
}