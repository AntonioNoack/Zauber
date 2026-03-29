package me.anno.zauber.ast.simple

import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class SimpleField(
    val type: Type, val ownership: Ownership, val id: Int,
    val scopeIfIsThis: Scope?,
) {

    constructor(type: Type, ownership: Ownership, id: Int) :
            this(type, ownership, id, null)

    var numReads = 0
    var mergeInfo: SimpleMerge? = null

    fun use(): SimpleField {
        numReads++
        return this
    }

    override fun toString(): String {
        return if (scopeIfIsThis == null) "%$id[$type]"
        else "this@$scopeIfIsThis[$type]"
    }
}