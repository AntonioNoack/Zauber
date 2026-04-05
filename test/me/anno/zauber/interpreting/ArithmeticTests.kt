package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArithmeticTests {

    @Test
    fun testSimpleIntCalculation() {
        val code = """
            val tested = 1+3*7
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(22, castToInt(value))
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
        assertEquals(21, castToInt(value))
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
        assertEquals(24, castToInt(value))
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
        assertEquals(24, castToInt(value))
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
        assertEquals(25, castToInt(value))
    }

}