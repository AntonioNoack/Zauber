package me.anno.support.javascript.ast

import me.anno.zauber.types.Type

class KeyOfType(val type: Type) : Type() {
    override fun toStringImpl(depth: Int): String = "keyof $type"
}