package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class UnresolvedUnionType(val types: List<Type>) : Type() {

    override fun toStringImpl(depth: Int): String = types.joinToString(" | ", "[", "]") { it.toString() }

    override fun not(): Type = UnresolvedNotType(this)

    override val resolvedName: Type
        get() = unionTypes(types.map { it.resolvedName })

}