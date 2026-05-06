package me.anno.zauber.types.impl.arithmetic

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ModifierType

class NotType(type: Type) : ModifierType(type) {

    init {
        check(type !is NotType)
    }

    override fun withType(type: Type) = type.not()

    override fun not(): Type = type

    override fun toStringImpl(depth: Int): String {
        return "NotType(${type.toString(depth)})"
    }

    override fun equals(other: Any?): Boolean {
        return other is NotType &&
                type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override val resolvedName: Type
        get() = type.resolvedName.not()
}