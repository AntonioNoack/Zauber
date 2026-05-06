package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.CollectionType
import me.anno.zauber.types.impl.arithmetic.AndType.Companion.andTypes

class UnresolvedAndType(types: List<Type>) : CollectionType(types) {

    override fun toStringImpl(depth: Int): String = types.joinToString(" & ", "[", "]") { it.toString() }

    override fun withTypes(types: List<Type>): Type = UnresolvedAndType(types)
    override fun not(): Type = UnresolvedNotType(this)

    override val resolvedName: Type
        get() = andTypes(types.map { it.resolvedName })

}