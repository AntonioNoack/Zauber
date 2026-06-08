package me.anno.support.cpp.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ModifierType

class ArrayType(type: Type, val size: Expression) : ModifierType(type) {
    override fun toStringImpl(depth: Int): String = "$type[$size]"
    override fun withType(type: Type): Type = ArrayType(type, size)
    override fun equals(other: Any?): Boolean {
        return other is ArrayType &&
                type == other.type &&
                size == other.size
    }

    override fun hashCode(): Int {
        return type.hashCode() * 31 + size.hashCode()
    }
}