package me.anno.zauber.types.impl

import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SelfType(val scope: Scope) : Type() {
    override fun toString(depth: Int): String = "Self"
}