package me.anno.zauber.types.impl

import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class UnresolvedSubType(
    val base: Type,
    val className: String,
    val scope: Scope,
    val imports: List<Import>
) : Type() {
    override fun toStringImpl(depth: Int): String {
        return "$base.$className?"
    }
}