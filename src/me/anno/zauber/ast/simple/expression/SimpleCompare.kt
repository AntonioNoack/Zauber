package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.members.ResolvedMethod

class SimpleCompare(
    dst: SimpleField,
    val left: SimpleField, val right: SimpleField, val type: CompareType,
    val method: ResolvedMethod,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${type.symbol} $right"
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val left = runtime[left]

        val method1 = method.specialization
        val result = runtime.executeCall(left, method1, listOf(right))
        if (!result.isValue()) return result

        val asInt = result.value.castToInt()
        val asBool = type.eval(asInt)
        return BlockReturn(ReturnType.VALUE, runtime.getBool(asBool))
    }
}