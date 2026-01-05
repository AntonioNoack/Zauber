package me.anno.zauber.ast.simple

class SimpleField(val id: Int) {

    var numReads = 0

    fun get(): SimpleField {
        numReads++
        return this
    }

    override fun toString(): String {
        return "%$id"
    }

}