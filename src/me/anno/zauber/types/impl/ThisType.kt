package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

/**
 * Method that returns 'this'.
 * This is a type of 'Self', that is automatically correct by inherited methods.
 * */
class ThisType(val type: Type) : Type() {
    override fun toStringImpl(depth: Int): String = "This($type)"
}