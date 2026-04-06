package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class FloatFormatTests {

    @Test
    fun testSimpleFloatField() {
        val value = testExecute("val tested = 17f")
        assertEquals(17f, value.castToFloat())
    }

    @Test
    fun testNegativeFloatField() {
        val code = """
            val tested = -17f
            
            package zauber
            class Float {
                external fun minus(other: Float): Float
                fun unaryMinus() = 0f - this
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(-17f, value.castToFloat())
    }

    @Test
    fun testFloatFieldWithExponent() {
        val value = testExecute("val tested = 17e3f")
        assertEquals(17e3f, value.castToFloat())
    }

    @Test
    fun testFloatFieldWithDecimalsAndExponent() {
        val value = testExecute("val tested = 17.31e3f")
        assertEquals(17.31e3f, value.castToFloat())
    }

    @Test
    fun testHexFloatField() {
        val value = testExecute("val tested = 0x1.4ap5f")
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(5), value.castToFloat())
    }

    @Test
    fun testHexFloatFieldNegativeBase() {
        val value = testExecute("val tested = 0x1.4ap-5f")
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(-5), value.castToFloat())
    }

    @Test
    fun testHexFloatFieldPlusBase() {
        val value = testExecute("val tested = 0x1.4ap+5f")
        assertEquals((1f + 4f / 16f + 10f / 256f) * 2f.pow(5), value.castToFloat())
    }

    @Test
    fun testHexDoubleField() {
        val value = testExecute("val tested = 0x1.4ap5")
        assertEquals((1.0 + 4.0 / 16.0 + 10.0 / 256.0) * 2.0.pow(5), value.castToDouble())
    }
}