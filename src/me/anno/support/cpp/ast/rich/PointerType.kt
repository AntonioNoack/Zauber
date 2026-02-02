package me.anno.support.cpp.ast.rich

import me.anno.zauber.types.Type

class PointerType(val baseType: Type) : Type() {

    override fun toStringImpl(depth: Int): String =
        "PointerType(${baseType.toString(depth)})"

    override fun equals(other: Any?): Boolean {
        return other is PointerType && baseType == other.baseType
    }

    override fun hashCode(): Int {
        return 1 + 31 * baseType.hashCode()
    }

    companion object {
        fun Type.ptr() = PointerType(this)
    }
}