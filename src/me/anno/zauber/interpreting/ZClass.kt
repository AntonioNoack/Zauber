package me.anno.zauber.interpreting

import me.anno.zauber.types.Scope

class ZClass(val clazz: Scope) {
    val properties = clazz.fields.filter { it.selfType == clazz.typeWithArgs }

    fun createInstance(): Instance {
        return Instance(this, arrayOfNulls(properties.size))
    }
}