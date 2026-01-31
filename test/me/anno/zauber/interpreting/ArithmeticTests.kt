package me.anno.zauber.interpreting

import me.anno.support.cpp.ast.rich.CppParsingTest.Companion.ensureUnitIsKnown
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArithmeticTests {

    @Test
    fun testSimpleIntCalculation() {
        ensureUnitIsKnown()
        val code = """
            val tested get() = 1+3*7
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(22, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntCalculationWithIntermediate() {
        ensureUnitIsKnown()
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
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(21, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntCalculationWithIntermediateAndInc() {
        ensureUnitIsKnown()
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
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(24, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntCalculationWithIntermediateAndPlusAssign() {
        ensureUnitIsKnown()
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
        val (runtime, value) = testExecute(code)
        assertEquals(IntType, value.type.type)
        assertEquals(24, runtime.castToInt(value))
    }

    @Test
    fun testSimpleIntCalculationByCall() {
        val code = """
            val tested get() = sq(5)
            fun sq(x: Int) = x*x
            
            package zauber
            class Int {
                external fun times(other: Int): Int
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals(25, runtime.castToInt(value))
    }

}