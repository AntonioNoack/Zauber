package me.anno.support.jvm

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.utils.assertEquals

// todo next step:
//   ClassType must include types from outer-classes ->
//     this will solve quite a lot of issues,
//     and also allow our code gen to continue on "Incomplete generics for java.util.HashMap.KeySet"

// todo read a complex class like HashMap,
//  and decode it fully into simple instructions...

// todo ideally, we have some jars and can just lazy-load all contents

// then try to instantiate and use an instance...
// todo we need to fix generics... ArrayList.add() must return E, not Object

// todo is there an interesting, non-generic class we can test?
//  -> we could use any class we create, and Kotlin/Java compiles for us...

// todo why TF is this exploring Regex???

fun main() {

    LogManager.disableLoggersCompletely("OverriddenMethods")

    // define some standard functions...
    testExecute(
        """
val tested = 0 // unused

package zauber
class Int {
    external operator fun plus(other: Int): Int
    external operator fun minus(other: Int): Int
    external operator fun times(other: Int): Int
    external operator fun div(other: Int): Int
    external operator fun compareTo(other: Int): Int
    operator fun unaryMinus(): Int = 0 - this
    external fun equals(other: Int): Boolean
    external operator fun and(other: Int): Int
    external operator fun or(other: Int): Int
    external operator fun xor(other: Int): Int
    external operator fun shl(other: Int): Int
    external operator fun shr(other: Int): Int
    external operator fun ushr(other: Int): Int
    external operator fun toChar(): Char
    external operator fun toByte(): Byte
    external operator fun toLong(): Long
}
class Char {}
class Byte {}
class Long {
    external operator fun plus(other: Long): Long
    external operator fun minus(other: Long): Long
    external operator fun times(other: Long): Long
    external operator fun div(other: Long): Long
    external operator fun compareTo(other: Long): Int
    operator fun unaryMinus(): Long = 0L - this
    external fun equals(other: Long): Boolean
    external operator fun and(other: Long): Long
    external operator fun or(other: Long): Long
    external operator fun xor(other: Long): Long
    external operator fun shl(other: Int): Long
    external operator fun shr(other: Int): Long
    external operator fun ushr(other: Int): Long
    external operator fun toInt(): Int
}
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    external operator fun set(index: Int, value: Any)
    external operator fun get(index: Int): V
}
    """.trimIndent()
    )

    registerJavaClass("java.util.ArrayList")

    val value = testExecute(
        """
        import java.util.ArrayList
        fun test(): Int {
            val x = ArrayList<Int>()
            x.add(1)
            return x[0]
        }
        
        val tested = test()
    """.trimIndent(), reset = false
    )
    assertEquals(1, value.castToInt())

}

fun registerJavaClass(path: String) {
    FirstJVMClassReader.getScope(path, null)
}