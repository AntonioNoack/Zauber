package me.anno.zauber.ast.simple

import me.anno.zauber.types.Type

class SimpleField(val type: Type, val id: Int) {

    var numReads = 0

    fun get(): SimpleField {
        numReads++
        return this
    }

    override fun toString(): String {
        return "%$id[$type]"
    }

}