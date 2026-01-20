package me.anno.zauber.interpreting

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ZClass(val type: Type) {
    val properties = (type as? ClassType)?.clazz?.fields
        ?.filter { it.selfType == type }
        ?: emptyList()

    private var objectInstance: Instance? = null

    fun getOrCreateObjectInstance(runtime: Runtime): Instance {
        var objectInstance = objectInstance
        if (objectInstance != null) return objectInstance
        objectInstance = createInstance()
        this.objectInstance = objectInstance
        val primaryConstructor = (type as? ClassType)?.clazz?.primaryConstructorScope?.selfAsConstructor
        if (primaryConstructor != null) {
            runtime.executeCall(objectInstance, primaryConstructor, emptyList())
        }
        return objectInstance
    }

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