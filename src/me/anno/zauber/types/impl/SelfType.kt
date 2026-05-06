package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

/**
 * The self-type represents whatever class or object surrounding the method is.
 * */
class SelfType(type: Type) : ModifierType(type) {

    val scope get() = (type as ClassType).clazz

    override fun withType(type: Type): Type = SelfType(type)

    override fun toStringImpl(depth: Int): String = "Self(${scope.pathStr})"
}