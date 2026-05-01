package me.anno.support.javascript.ast

import me.anno.zauber.types.Type

class FieldOfType(val type: Type, val name: Type) : Type() {
    override fun toStringImpl(depth: Int): String = "$type[$name]"
}