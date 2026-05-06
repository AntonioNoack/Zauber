package me.anno.zauber.types.impl.memory

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ModifierType
import me.anno.zauber.types.impl.memory.ValueType.Companion.noRefNorValue

/**
 * Holds instances by reference, even if they are value-types
 * todo test these...
 * */
class RefType(type: Type) : ModifierType(type.noRefNorValue()) {

    override fun withType(type: Type): Type = RefType(type)

    override fun toStringImpl(depth: Int): String {
        return "ref ${type.toStringImpl(depth)}"
    }
}