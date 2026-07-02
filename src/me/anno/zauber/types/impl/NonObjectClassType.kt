package me.anno.zauber.types.impl

import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type

class NonObjectClassType(val type: ClassType) : Type() {

    override val resolvedName: Type get() = type

    override fun specialize(spec: Specialization): Type {
        return NonObjectClassType(type.specialize(spec))
    }

    override fun toStringImpl(depth: Int): String {
        return "N[${type.toStringImpl(depth)}]"
    }
}