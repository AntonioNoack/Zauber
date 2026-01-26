package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope

class SimpleCompare(
    dst: SimpleField, val left: SimpleField, val right: SimpleField, val type: CompareType,
    val callable: ResolvedMethod, scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${type.symbol} $right"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val left = runtime[left]
        val rawValue = runtime.executeCall(left, callable.resolved, listOf(right))
        // todo a yield should be handled differently...
        //  on the other hand, who would yield inside a compare function? XD
        return if (rawValue.type == ReturnType.RETURN) {
            val asInt = runtime.castToInt(rawValue.instance)
            val asBool = type.eval(asInt)
            BlockReturn(ReturnType.VALUE, runtime.getBool(asBool))
        } else rawValue
    }
}