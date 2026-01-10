package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.impl.ClassType

class SuperCall(
    val type: ClassType,
    val valueParameters: List<NamedParameter>?,
    val delegate: Expression?
) {
    override fun toString(): String {
        return "$type($valueParameters) by $delegate"
    }
}