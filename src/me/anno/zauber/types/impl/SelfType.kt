package me.anno.zauber.types.impl

import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * The self-type represents whatever class or object surrounding the method is.
 * */
class SelfType(val scope: Scope) : Type() {
    override fun toStringImpl(depth: Int): String = "Self"
}