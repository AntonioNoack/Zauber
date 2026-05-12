package me.anno.zauber.types.impl.memory

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ModifierType

/**
 * Holds instances by reference, even if they are value-types
 * todo test these...
 *
 * todo generate synthetic classes for value X / ref X
 * */
class ValueType(type: Type) : ModifierType(type.noRefNorValue()) {

    override fun withType(type: Type): Type = ValueType(type)

    override fun toStringImpl(depth: Int): String {
        return "value ${type.toStringImpl(depth)}"
    }

    companion object {
        fun Type.noRefNorValue(): Type {
            return when (this) {
                is ValueType -> type
                is RefType -> type
                else -> this
            }
        }
    }
}