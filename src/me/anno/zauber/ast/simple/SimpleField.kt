package me.anno.zauber.ast.simple

// todo we should make these fields typed (mainly for WASM)
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