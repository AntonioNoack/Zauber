package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type

class SimpleField(val type: Type, var id: Int, val constantRef: Expression?) {

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