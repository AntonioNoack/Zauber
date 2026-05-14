package me.anno.zauber.interpreting

import me.anno.generation.LoggerUtils.disableCompileLoggers
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhenTests {

    // todo these fail with root-mismatch... we must store some references somewhere...

    private val stdlib = """
package zauber

class Any {
    open fun equals(other: Any): Boolean = this === other
}
class Int {
    external operator fun plus(other: Int): Int
    external operator fun compareTo(other: Int): Int
    operator fun inc(): Int = this + other
    fun equals(other: Int): Boolean = this >= other && this <= other
}

enum class Boolean {
    TRUE, FALSE;
    
    fun not(): Boolean = this == FALSE
}

class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    
    fun contains(value: V) = indexOf(value) >= 0
    fun indexOf(value: V): Int {
        var i = 0
        while (i < size) {
            if (this[i] == value) return i
            i++
        }
        return -1
    }
}

fun <V> arrayOf(vararg vs: V): Array<V> = vs
    """.trimIndent()

    @Test
    fun testWhenWithDuplicateCases() {
        // todo forbid this when the names are literally the same?
        //  warning, when just the values are the same
        val code = """
            val tested = when (1) {
                1 -> 10
                1 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(10, testExecute(code).castToInt())
    }

    @Test
    fun testWhenSecondCase() {
        val code = """
            val tested = when (2) {
                1 -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(20, testExecute(code).castToInt())
    }

    @Test
    fun testWhenElseCase() {
        val code = """
            val tested = when (5) {
                1 -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(30, testExecute(code).castToInt())
    }

    @Test
    fun testWhenWithIn() {
        // disableCompileLoggers()
        val code = """
            val tested = when (1) {
                in arrayOf(1,2,3) -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(10, testExecute(code).castToInt())
    }

    @Test
    fun testWhenWithNotIn() {
        val code = """
            val tested = when (1) {
                !in arrayOf(1,2,3) -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(30, testExecute(code).castToInt())
    }

    @Test
    fun testWhenWithIs() {
        val code = """
            val tested = when (1) {
                is Int -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(10, testExecute(code).castToInt())
    }

    @Test
    fun testWhenWithNotIs() {
        val code = """
            val tested = when (1) {
                !is Int -> 10
                2 -> 20
                else -> 30
            }
        """.trimIndent() + stdlib
        assertEquals(30, testExecute(code).castToInt())
    }

    // todo when with bool conditions
    // todo when with type-check and if-after

}