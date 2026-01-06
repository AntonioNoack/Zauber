package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

class SubType(val parent: Type, val child: Type) : Type() {
    override fun toStringImpl(depth: Int): String {
        return parent.toString(depth) + "." + child.toString(depth)
    }
}