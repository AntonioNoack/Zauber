package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class FloatFormatTests {

    @Test
    fun testSimpleFloatField() {
        val float = testExecute("val tested = 17f")
        assertEquals(17f, float.castToFloat())

        val double = testExecute("val tested = 17.0")
        assertEquals(17.0, double.castToDouble())
    }

    @Test
    fun testNegativeFloatField() {
        val float = testExecute("val tested = -17f")
        assertEquals(-17f, float.castToFloat())

        val double = testExecute("val tested = -17.0")
        assertEquals(-17.0, double.castToDouble())
    }

    @Test
    fun testFloatFieldWithExponent() {
        val float = testExecute("val tested = 17e3f")
        assertEquals(17e3f, float.castToFloat())

        val double = testExecute("val tested = 17e3")
        assertEquals(17e3, double.castToDouble())
    }

    @Test
    fun testFloatFieldWithDecimalsAndExponent() {
        val float = testExecute("val tested = 17.31e3f")
        assertEquals(17.31e3f, float.castToFloat())

        val double = testExecute("val tested = 17.32e3")
        assertEquals(17.32e3, double.castToDouble())
    }

    @Test
    fun testHexFloatField() {
        val float = testExecute("val tested = 0x1.4ap5f")
        assertEquals((0x1 + 0x4 / 16f + 0xa / 256f) * 2f.pow(5), float.castToFloat())

        val double = testExecute("val tested = 0x1.4ap5")
        assertEquals((0x1 + 0x4 / 16.0 + 0xa / 256.0) * 2.0.pow(5), double.castToDouble())
    }

    @Test
    fun testHexFloatFieldNegativeBase() {
        val float = testExecute("val tested = 0x1.4ap-5f")
        assertEquals((0x1 + 0x4 / 16f + 0xa / 256f) * 2f.pow(-5), float.castToFloat())

        val double = testExecute("val tested = 0x1.4ap-5")
        assertEquals((0x1 + 0x4 / 16.0 + 0xa / 256.0) * 2.0.pow(-5), double.castToDouble())
    }

    @Test
    fun testHexFloatFieldPlusBase() {
        val float = testExecute("val tested = 0x1.4ap+5f")
        assertEquals((0x1 + 0x4 / 16f + 0xa / 256f) * 2f.pow(5), float.castToFloat())

        val double = testExecute("val tested = 0x1.4ap+5")
        assertEquals((0x1 + 0x4 / 16.0 + 0xa / 256.0) * 2.0.pow(5), double.castToDouble())
    }

    @Test
    fun testBinFloatField() {
        val float = testExecute("val tested = 0b1011p5f")
        assertEquals(0b1011 * 2f.pow(5), float.castToFloat())

        val double = testExecute("val tested = 0b1011p5")
        assertEquals(0b1011 * 2.0.pow(5), double.castToDouble())
    }

    @Test
    fun testBinFloatFieldNegativeBase() {
        val float = testExecute("val tested = 0b1011p-5f")
        assertEquals(0b1011 * 2f.pow(-5), float.castToFloat())

        val double = testExecute("val tested = 0b1011p-5")
        assertEquals(0b1011 * 2.0.pow(-5), double.castToDouble())
    }

    @Test
    fun testBinFloatFieldPlusBase() {
        val float = testExecute("val tested = 0b1011p+5f")
        assertEquals(0b1011 * 2f.pow(5), float.castToFloat())

        val double = testExecute("val tested = 0b1011p+5")
        assertEquals(0b1011 * 2.0.pow(5), double.castToDouble())
    }

    @Test
    fun testLargeDecimalBasis() {
        val float = testExecute("val tested = 1e1" + "0".repeat(20) + "f")
        assertEquals(Float.POSITIVE_INFINITY, float.castToFloat())

        val double = testExecute("val tested = 1e1" + "0".repeat(20))
        assertEquals(Double.POSITIVE_INFINITY, double.castToDouble())
    }

    @Test
    fun testLargeBinaryBasis() {
        val float = testExecute("val tested = 0x1p1" + "0".repeat(20) + "f")
        assertEquals(Float.POSITIVE_INFINITY, float.castToFloat())

        val double = testExecute("val tested = 0x1p1" + "0".repeat(20))
        assertEquals(Double.POSITIVE_INFINITY, double.castToDouble())
    }
}