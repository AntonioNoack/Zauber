package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhenTests {

    private val stdlib = """
package zauber
class Any
class Int {
    external operator fun compareTo(other: Int): Int
    fun equals(other: Int): Boolean = this >= other && this <= other
}
enum class Boolean { TRUE, FALSE }
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
}
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

    // todo when with 'is', 'in', '!is', '!in'
    // todo when with bool conditions

}