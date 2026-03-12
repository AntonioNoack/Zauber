package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope

class SimpleCheckIdentical(
    dst: SimpleField, val left: SimpleField, val right: SimpleField,
    val negated: Boolean,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${if (negated) "!==" else "==="} $right"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val left = runtime[left]
        val right = runtime[right]
        val instance = runtime.getBool((left == right) xor negated)
        return BlockReturn(ReturnType.VALUE, instance)
    }
}