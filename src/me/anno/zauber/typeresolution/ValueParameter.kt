package me.anno.zauber.typeresolution

import me.anno.zauber.types.Type

abstract class ValueParameter(val name: String?) {
    abstract fun getType(targetType: Type): Type
}