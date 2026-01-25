package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleCheckIdentical(
    dst: SimpleField, val left: SimpleField, val right: SimpleField,
    val negated: Boolean,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${if (negated) "!==" else "==="} $right"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val instance = runtime.getBool(runtime[left] == runtime[right])
        return BlockReturn(ReturnType.VALUE, instance)
    }

}