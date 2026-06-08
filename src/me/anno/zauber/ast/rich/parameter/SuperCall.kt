package me.anno.zauber.ast.rich.parameter

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class SuperCall(
    val typeI: Type,
    val valueParameters: List<NamedParameter>?,
    val delegate: Expression?,
    val origin: Long,
) {

    val type: ClassType
        get() = typeI.resolvedName as ClassType

    val isClassCall: Boolean
        get() = !isInterfaceCall
    val isInterfaceCall: Boolean
        get() = valueParameters == null

    override fun toString(): String {
        val prefix = "$type(${valueParameters?.joinToString(", ")})"
        return if (delegate != null) "$prefix by $delegate" else prefix
    }
}