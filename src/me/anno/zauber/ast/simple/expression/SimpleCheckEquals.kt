package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope
import me.anno.zauber.types.specialization.MethodSpecialization

class SimpleCheckEquals(
    dst: SimpleField,
    val left: SimpleField, val right: SimpleField,
    val negated: Boolean,
    val method: ResolvedMethod,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${if (negated) "!=" else "=="} $right"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val va = runtime[left]
        val vb = runtime[right]
        if (va == vb) return BlockReturn(ReturnType.VALUE, runtime.getBool(true))

        val vaNull = runtime.isNull(va)
        val vbNull = runtime.isNull(vb)
        if (vaNull != vbNull) return BlockReturn(ReturnType.VALUE, runtime.getBool(false))

        // todo handle error from this...
        val method1 = MethodSpecialization(method.resolved, method.specialization)
        return runtime.executeCall(va, method1, listOf(right))
    }
}