package me.anno.zauber.ast.simple

import me.anno.zauber.ast.simple.expression.SimpleMerge
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo 'this' is a special field, that cannot be reassigned
//  we therefore don't need assignments for it, but just inline access
class SimpleField(
    val type: Type, val ownership: Ownership, val id: Int,
    val scopeIfIsThis: Scope?
) {

    var numReads = 0
    var mergeInfo: SimpleMerge? = null

    fun use(): SimpleField {
        numReads++
        return this
    }

    override fun toString(): String {
        return "%$id[$type]"
    }
}