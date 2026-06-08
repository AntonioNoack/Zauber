package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.unresolved.UnresolvedClassType

/**
 * The self-type represents whatever class or object surrounding the method is.
 * */
class SelfType(type: Type ) : ModifierType(type) {

    val scope get() = when(type){
        is ClassType -> type.clazz
        is UnresolvedClassType -> type.clazz
        else -> (type.resolvedName as ClassType).clazz
    }

    override fun withType(type: Type): Type = SelfType(type)

    override fun toStringImpl(depth: Int): String = "Self($scope)"
}