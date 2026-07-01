package me.anno.zauber.interpreting

import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Test

class WhenTests {

    // todo these fail with root-mismatch... we must store some references somewhere...

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
        """.trimIndent()
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
        """.trimIndent()
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
        """.trimIndent()
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
        """.trimIndent()
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
        """.trimIndent()
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
        """.trimIndent()
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
        """.trimIndent()
        assertEquals(30, testExecute(code).castToInt())
    }

    // todo when with bool conditions
    // todo when with type-check and if-after

}