package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ArithmeticTests {

    @ParameterizedTest
    @ValueSource(strings = ["type", "runtime"])
    fun testSimpleIntCalculation(type: String) {
        val code = """
            val tested = 1+3*7
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        MultiTest()
            .type(code) { Types.Int }
            .runtime(code) { value ->
                assertEquals(22, value.castToInt())
            }.run(type)
    }

    @Test
    fun testSimpleIntCalculationWithIntermediate() {
        val code = """
            val tested: Int
                get() {
                    var tmp = 1 + 6
                    return tmp * 3
                }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(21, value.castToInt())
    }

    @Test
    fun testSimpleIntCalculationWithIntermediateAndInc() {
        val code = """
            val tested: Int
                get() {
                    var tmp = 1+6
                    tmp++
                    return tmp * 3
                }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
                fun inc() = this+1
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(24, value.castToInt())
    }

    @Test
    fun testSimpleIntCalculationWithIntermediateAndPlusAssign() {
        val code = """
            val tested: Int
                get() {
                    var tmp = 1+6
                    tmp += 1
                    return tmp * 3
                }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
                fun inc() = this+1
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(24, value.castToInt())
    }

    @Test
    fun testSimpleIntCalculationByCall() {
        val code = """
            val tested = sq(5)
            fun sq(x: Int) = x*x
            
            package zauber
            class Int {
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(25, value.castToInt())
    }

}