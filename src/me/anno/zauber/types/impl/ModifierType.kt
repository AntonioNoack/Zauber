package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

abstract class ModifierType(val type: Type): Type() {
    abstract fun withType(type: Type): Type
}