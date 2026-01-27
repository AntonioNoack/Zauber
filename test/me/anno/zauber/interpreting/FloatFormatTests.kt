package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToDouble
import me.anno.zauber.interpreting.RuntimeCast.castToFloat
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class FloatFormatTests {

    @Test
    fun testSimpleFloatField() {
        val code = """
            val tested get() = 17f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17f, runtime.castToFloat(value))
    }

    @Test
    fun testNegativeFloatField() {
        val code = """
            val tested get() = -17f
            
            package zauber
            class Float {
                external fun minus(other: Float): Float
                fun unaryMinus() = 0f - this
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(-17f, runtime.castToFloat(value))
    }

    @Test
    fun testFloatFieldWithExponent() {
        val code = """
            val tested get() = 17e3f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17e3f, runtime.castToFloat(value))
    }

    @Test
    fun testFloatFieldWithDecimalsAndExponent() {
        val code = """
            val tested get() = 17.31e3f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(17.31e3f, runtime.castToFloat(value))
    }

    @Test
    fun testHexFloatField() {
        val code = """
            val tested get() = 0x1.4ap5f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(5), runtime.castToFloat(value))
    }

    @Test
    fun testHexFloatFieldNegativeBase() {
        val code = """
            val tested get() = 0x1.4ap-5f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(-5), runtime.castToFloat(value))
    }

    @Test
    fun testHexFloatFieldPlusBase() {
        val code = """
            val tested get() = 0x1.4ap+5f
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(5), runtime.castToFloat(value))
    }

    @Test
    fun testHexDoubleField() {
        val code = """
            val tested get() = 0x1.4ap5
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals((1.0 + 4.0 / 16.0 + 10.0 / 256.0) * 2.0.pow(5), runtime.castToDouble(value))
    }

}