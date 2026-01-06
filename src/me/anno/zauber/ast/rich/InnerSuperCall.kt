package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.CallExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.types.Scope

enum class InnerSuperCallTarget {
    THIS,
    SUPER
}

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>,
    val scope: Scope,
    val origin: Int
) {
    fun toExpr(): CallExpression {
        val nameType = if (target == InnerSuperCallTarget.THIS) SpecialValue.THIS else SpecialValue.SUPER
        val name = SpecialValueExpression(nameType, scope, origin)
        return CallExpression(name, emptyList(), valueParameters, origin)
    }
}