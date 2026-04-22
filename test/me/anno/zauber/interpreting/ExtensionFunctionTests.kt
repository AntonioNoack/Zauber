package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionFunctionTests {
    @Test
    fun testGetMethodField() {
        val code = """
            fun Int.calc(): Int {
                return this + 2
            }
            val tested = 1.calc()
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testGetMemberField() {
        val code = """
            class A(val x: Int)
            fun A.calc(): Int {
                return x
            }
            val tested = A(3).calc()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testSetMethodField() {
        val code = """
            fun Int.calc(): Int {
                var x = 2
                x = 3
                return x
            }
            val tested = 0.calc()
            
            package zauber
            class Int
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testSetMemberField() {
        val code = """
            class A(var x: Int)
            fun A.calc(): A {
                x = 5
                return this
            }
            val tested = A(3).calc().x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(5, value.castToInt())
    }

    @Test
    fun testCallMethodFunction() {
        val code = """
            fun Int.calc(): Int {
                fun inner() = 2
                return inner()
            }
            val tested = 0.calc()
            
            package zauber
            class Int
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testCallMemberFunction() {
        val code = """
            class A {
                fun x() = 3
            }
            fun A.calc(): Int {
                return x()
            }
            val tested = A().calc()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }
}