package me.anno.zauber.types.impl

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

/**
 * Exactly null
 * */
object NullType : Type() {
    override fun toString(depth: Int): String = "NullType"

    fun typeOrNull(base: Type): Type {
        return unionTypes(base, NullType)
    }
}