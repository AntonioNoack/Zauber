package me.anno.support.jvm

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute

fun main() {
    // todo read a complex class like HashMap,
    //  and decode it fully into simple instructions...

    // todo first select a target class to inspect...
    //  maybe clear()

    // define some standard functions...
    testExecute("""
val tested = 0 // unused

package zauber
class Int {
    external operator fun plus(other: Int): Int
    external operator fun compareTo(other: Int): Int
}
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    external operator fun set(index: Int, value: Any)
    external operator fun get(index: Int): V
}
    """.trimIndent())

    JVMClassReader.getScope("java/util/ArrayList", null)
}