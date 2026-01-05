package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleCall(
    dst: SimpleField,
    val method: Method,
    val base: SimpleField,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    init {
        check(method.valueParameters.size == valueParameters.size)
    }

    override fun toString(): String {
        return "$dst = $base[${method.selfType}].${method.name}${valueParameters.joinToString(", ", "(", ")")}"
    }

}