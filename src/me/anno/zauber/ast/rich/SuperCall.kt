package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class SuperCall(
    val typeI: Type,
    val valueParameters: List<NamedParameter>?,
    val delegate: Expression?
) {
    val type: ClassType
        get() = typeI.resolved as ClassType

    override fun toString(): String {
        return "$type($valueParameters) by $delegate"
    }
}