package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.impl.ClassType

class SimpleAllocateInstance(
    dst: SimpleField,
    val allocatedType: ClassType,
    val paramsForLater: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = new $allocatedType"
    }

    override fun eval(): BlockReturn {
        val newInstance = runtime.getClass(allocatedType).createInstance()
        return BlockReturn(ReturnType.VALUE, newInstance)
    }
}