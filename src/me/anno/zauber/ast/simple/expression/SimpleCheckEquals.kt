package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleCheckEquals(
    dst: SimpleField, val left: SimpleField, val right: SimpleField,
    val negated: Boolean,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${if (negated) "!=" else "=="} $right"
    }

    override fun eval(runtime: Runtime): Instance {
        val va = runtime[left]
        val vb = runtime[right]
        if (va == vb) return runtime.getBool(true)
        val vaNull = runtime.isNull(va)
        val vbNull = runtime.isNull(vb)
        if (vaNull != vbNull) return runtime.getBool(false)
        TODO("Run the .equals() method")
    }

}