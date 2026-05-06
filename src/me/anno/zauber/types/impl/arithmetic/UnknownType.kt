package me.anno.zauber.types.impl.arithmetic

import me.anno.zauber.types.Type

/**
 * Anything within the generic bounds...
 * */
object UnknownType : Type() {
    override fun toStringImpl(depth: Int): String = "UnknownType"
}