package me.anno.zauber.interpreting

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ZClass(val type: Type) {
    val properties = (type as? ClassType)?.clazz?.fields
        ?.filter { it.selfType == type }
        ?: emptyList()

    fun createInstance(): Instance {
        return Instance(this, arrayOfNulls(properties.size))
    }

    fun isSubTypeOf(expectedType: ZClass): Boolean {
        TODO()
    }

    override fun toString(): String {
        return "ZClass($type,${properties.map { it.name }})"
    }
}