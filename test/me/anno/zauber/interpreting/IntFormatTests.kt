package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntFormatTests {

    @Test
    fun testSimpleIntField() {
        val int = testExecute("val tested = 17")
        assertEquals(17, int.castToInt())

        val long = testExecute("val tested = 17L")
        assertEquals(17L, long.castToLong())
    }

    @Test
    fun testNegativeIntField() {
        val int = testExecute("val tested = -17")
        assertEquals(-17, int.castToInt())

        val long = testExecute("val tested = -17L")
        assertEquals(-17L, long.castToLong())
    }

    @Test
    fun testHexIntField() {
        val int = testExecute("val tested = 0x17")
        assertEquals(23, int.castToInt())

        val long = testExecute("val tested = 0x17L")
        assertEquals(23L, long.castToLong())
    }

    @Test
    fun testBinIntField() {
        val int = testExecute("val tested = 0b10101")
        assertEquals(21, int.castToInt())

        val long = testExecute("val tested = 0b10101L")
        assertEquals(21L, long.castToLong())
    }

    @Test
    fun testIntMinMaxDec() {
        val min = testExecute("val tested = ${Int.MIN_VALUE}")
        assertEquals(Int.MIN_VALUE, min.castToInt())

        val max = testExecute("val tested = ${Int.MAX_VALUE}")
        assertEquals(Int.MAX_VALUE, max.castToInt())

        val min1 = testExecute("val tested = ${Int.MIN_VALUE - 1L}")
        assertEquals(Int.MIN_VALUE - 1L, min1.castToLong())

        val max1 = testExecute("val tested = ${Int.MAX_VALUE + 1L}")
        assertEquals(Int.MAX_VALUE + 1L, max1.castToLong())
    }

    @Test
    fun testIntMinMaxBin() {
        val min = testExecute("val tested = -0b1${"0".repeat(31)}")
        assertEquals(Int.MIN_VALUE, min.castToInt())

        val max = testExecute("val tested = 0b${Int.MAX_VALUE.toString(2)}")
        assertEquals(Int.MAX_VALUE, max.castToInt())

        val min1 = testExecute("val tested = -0b1${"0".repeat(30)}1")
        assertEquals(Int.MIN_VALUE - 1L, min1.castToLong())

        val max1 = testExecute("val tested = 0b${(Int.MAX_VALUE + 1L).toString(2)}")
        assertEquals(Int.MAX_VALUE + 1L, max1.castToLong())
    }

    @Test
    fun testIntMinMaxHex() {
        val min = testExecute("val tested = -0x8000_0000")
        assertEquals(Int.MIN_VALUE, min.castToInt())

        val max = testExecute("val tested = 0x7fff_ffff")
        assertEquals(Int.MAX_VALUE, max.castToInt())

        val min1 = testExecute("val tested = -0x8000_0001")
        assertEquals(Int.MIN_VALUE - 1L, min1.castToLong())

        val max1 = testExecute("val tested = 0x8000_0000")
        assertEquals(Int.MAX_VALUE + 1L, max1.castToLong())
    }

    @Test
    fun testIntHexMinMax() {
        val fits = testExecute("val tested = 0x7fff_ffff")
        assertEquals(0x7fff_ffff, fits.castToInt())

        val noFits = testExecute("val tested = 0x8000_0000")
        assertEquals(0x8000_0000L, noFits.castToLong())
    }

    @Test
    fun testLongMinMax() {
        val min = testExecute("val tested = ${Long.MIN_VALUE}")
        assertEquals(Long.MIN_VALUE, min.castToLong())

        val max = testExecute("val tested = ${Long.MAX_VALUE}")
        assertEquals(Long.MAX_VALUE, max.castToLong())
    }

}