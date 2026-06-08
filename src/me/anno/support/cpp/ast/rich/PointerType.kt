package me.anno.support.cpp.ast.rich

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ModifierType

class PointerType(type: Type) : ModifierType(type) {

    override fun toStringImpl(depth: Int): String =
        "PointerType(${type.toString(depth)})"

    override fun equals(other: Any?): Boolean {
        return other is PointerType && type == other.type
    }

    override fun hashCode(): Int {
        return 1 + 31 * type.hashCode()
    }

    override fun withType(type: Type): Type = PointerType(type)

    companion object {
        fun Type.ptr() = PointerType(this)
    }
}