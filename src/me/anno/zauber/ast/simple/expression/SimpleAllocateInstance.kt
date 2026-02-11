package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.impl.ClassType

class SimpleAllocateInstance(
    dst: SimpleField,
    val selfType: ClassType,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    init {
        if (selfType.clazz.typeParameters.isNotEmpty() && selfType.typeParameters == null)
            throw IllegalStateException("Illegal $selfType creation: all types must be known")
    }

    override fun toString(): String {
        return "$dst = new $selfType"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val newInstance = runtime.getClass(selfType).createInstance()
        return BlockReturn(ReturnType.VALUE, newInstance)
    }
}