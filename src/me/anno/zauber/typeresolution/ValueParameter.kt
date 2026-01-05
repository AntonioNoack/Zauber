package me.anno.zauber.typeresolution

import me.anno.zauber.types.Type

abstract class ValueParameter(val name: String?, val hasVarargStar: Boolean) {
    abstract fun getType(targetType: Type): Type
}