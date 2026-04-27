package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.NotType

class UnresolvedNotType(val type: Type) : Type() {

    override fun toStringImpl(depth: Int): String = "!${type.toString(depth)}"

    override fun not(): Type = type

    override val resolvedName: Type
        get() = NotType(type.resolvedName)

}