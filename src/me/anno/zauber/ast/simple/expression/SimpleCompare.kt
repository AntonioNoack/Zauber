package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleCompare(dst: SimpleField, val base: SimpleField, val type: CompareType, scope: Scope, origin: Int) :
    SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $base ${type.symbol} 0"
    }

    override fun eval(runtime: Runtime): Instance {
        val value = runtime[base]
        val asInt = runtime.castToInt(value)
        val result = when (type) {
            CompareType.LESS -> asInt < 0
            CompareType.LESS_EQUALS -> asInt <= 0
            CompareType.GREATER -> asInt > 0
            CompareType.GREATER_EQUALS -> asInt >= 0
        }
        return runtime.getBool(result)
    }
}