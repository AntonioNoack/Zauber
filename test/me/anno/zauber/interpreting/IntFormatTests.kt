package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntFormatTests {

    @Test
    fun testSimpleIntField() {
        val code = """
            val tested = 17
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(17, value.castToInt())
    }

    @Test
    fun testNegativeIntField() {
        LogManager.disableLoggers(
            "ResolvedMethod,FieldExpression,FieldResolver," +
                    "MemberResolver,TypeResolution,Inheritance,FieldResolver"
        )
        val code = """
            val tested = -17
            
            package zauber
            class Int {
                external fun minus(other: Int): Int
                fun unaryMinus() = 0 - this
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(-17, value.castToInt())
    }

    @Test
    fun testHexIntField() {
        val code = """
            val tested = 0x17
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(23, value.castToInt())
    }

    @Test
    fun testBinIntField() {
        val code = """
            val tested = 0b10101
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(21, value.castToInt())
    }

}