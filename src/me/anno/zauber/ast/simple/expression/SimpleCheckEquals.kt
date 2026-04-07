package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.members.ResolvedMethod
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

    override fun eval(): BlockReturn {
        val runtime = runtime
        val va = runtime[left]
        val vb = runtime[right]
        if (va == vb) return BlockReturn(ReturnType.VALUE, runtime.getBool(!negated))

        val vaNull = runtime.isNull(va)
        val vbNull = runtime.isNull(vb)
        if (vaNull != vbNull) return BlockReturn(ReturnType.VALUE, runtime.getBool(negated))

        // todo handle yield from this...
        val method1 = MethodSpecialization(method.resolved, method.specialization)
        val result = runtime.executeCall(va, method1, listOf(right))
        if (!negated) return result

        return when (result.type) {
            ReturnType.THROW -> result
            ReturnType.RETURN, ReturnType.VALUE -> {
                val value = result.value.castToBool()
                BlockReturn(ReturnType.VALUE, runtime.getBool(!value))
            }
            else -> throw NotImplementedError("$result in SimpleCheckEquals")
        }
    }
}