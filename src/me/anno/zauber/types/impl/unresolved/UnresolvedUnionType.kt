package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.CollectionType
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes

class UnresolvedUnionType(types: List<Type>) : CollectionType(types) {

    override fun toStringImpl(depth: Int): String = types.joinToString(" | ", "[", "]") { it.toString() }

    override fun withTypes(types: List<Type>): Type = UnresolvedUnionType(types)
    override fun not(): Type = UnresolvedNotType(this)

    override val resolvedName: Type
        get() = unionTypes(types.map { it.resolvedName })

}