package me.anno.zauber.typeresolution

import me.anno.zauber.types.Type

class ValueParameterImpl(name: String?, val type: Type) : ValueParameter(name) {
    override fun getType(targetType: Type): Type = this.type
    override fun toString(): String {
        return if (name != null) "$name=$type" else "$type"
    }
}