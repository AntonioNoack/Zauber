package me.anno.zauber.ast.simple

import java.util.concurrent.atomic.AtomicInteger

class SimpleField {
    val uuid = Companion.uuid.getAndIncrement()
    var numReads = 0

    fun get(): SimpleField {
        numReads++
        return this
    }

    companion object {
        private val uuid = AtomicInteger()
    }
}