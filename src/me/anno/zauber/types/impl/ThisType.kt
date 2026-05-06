package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

/**
 * Method that returns 'this'.
 * This is a type of 'Self', that is automatically correct by inherited methods.
 * */
class ThisType(type: Type) : ModifierType(type) {
    override fun withType(type: Type): Type = ThisType(type)
    override fun toStringImpl(depth: Int): String = "This($type)"
}