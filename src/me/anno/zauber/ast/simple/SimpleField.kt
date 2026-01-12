package me.anno.zauber.ast.simple

import me.anno.zauber.ast.simple.expression.SimpleMerge
import me.anno.zauber.types.Type

class SimpleField(val type: Type, val ownership: Ownership, val id: Int) {

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