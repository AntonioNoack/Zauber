package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

class NotType(val type: Type) : Type() {

    init {
        check(type !is NotType)
    }

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
}