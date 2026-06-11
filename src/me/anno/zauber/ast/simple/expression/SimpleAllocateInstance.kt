package me.anno.zauber.ast.simple.expression

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.utils.assertEquals
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.impl.ClassType

class SimpleAllocateInstance(
    dst: SimpleField,
    val allocatedType: ClassType,
    val paramsForLater: List<SimpleField>,
    val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    init {
        assertEquals(allocatedType.clazz, specialization.scope)
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime.getClass(allocatedType).createInstance()
        return null
    }

    override fun toString(): String {
        return "$dst = ${style("new", StringStyles.ORANGE)} " +
                style(allocatedType.toString(), StringStyles.LIGHT_ORANGE)
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleAllocateInstance(
            src.cloned(this.dst, dst), allocatedType,
            paramsForLater.map { src.cloned(it, dst) },
            specialization, scope, origin
        )
    }

}