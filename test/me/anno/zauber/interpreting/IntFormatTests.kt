package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntFormatTests {

    @Test
    fun testSimpleIntField() {
        val code = """
            val tested = 17
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17, runtime.castToInt(value))
    }

    @Test
    fun testNegativeIntField() {
        val code = """
            val tested = -17
            
            package zauber
            class Int {
                external fun minus(other: Int): Int
                fun unaryMinus() = 0 - this
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(-17, runtime.castToInt(value))
    }

    @Test
    fun testHexIntField() {
        val code = """
            val tested = 0x17
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(23, runtime.castToInt(value))
    }

    @Test
    fun testBinIntField() {
        val code = """
            val tested = 0b10101
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(21, runtime.castToInt(value))
    }

}