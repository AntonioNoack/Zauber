package me.anno.zauber.ast.simple

import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

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