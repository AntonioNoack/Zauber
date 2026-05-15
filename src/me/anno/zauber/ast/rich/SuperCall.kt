package me.anno.zauber.ast.rich

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

    val isClassCall: Boolean get() = !isInterfaceCall
    val isInterfaceCall: Boolean
        get() {
            if (valueParameters != null) return false

            check(type.clazz.scopeType != null) {
                "Missing scopeType information for $typeI, is it an interface?"
            }
            return type.clazz.isInterface()
        }

    override fun toString(): String {
        return "$type($valueParameters) by $delegate"
    }
}