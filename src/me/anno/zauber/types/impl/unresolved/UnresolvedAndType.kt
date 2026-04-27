package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.AndType.Companion.andTypes

class UnresolvedAndType(val types: List<Type>) : Type() {

    override fun toStringImpl(depth: Int): String = types.joinToString(" & ", "[", "]") { it.toString() }

    override fun not(): Type = UnresolvedNotType(this)

    override val resolvedName: Type
        get() = andTypes(types.map { it.resolvedName })

}