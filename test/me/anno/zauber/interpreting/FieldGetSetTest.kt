package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class FieldGetSetTest {

    companion object {
        inline fun <reified V : Throwable> assertThrows(validateMessage: (v: V) -> Unit, run: () -> Unit) {
            try {
                run()
            } catch (e: Throwable) {
                check(e is V) { "Incorrect exception type was thrown: $e" }
                validateMessage(e)
                return
            }
            fail { "Expected an exception to be thrown" }
        }
    }

    @Test
    fun testGetSetClassField() {
        val code = """
            class Vector(var x: Int, var y: Int)
            fun calculate(v: Vector): Int {
                v.x = 1
                return v.x + v.y
            }
            val tested = calculate(Vector(3,4))
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(5, value.castToInt())
    }

    @Test
    fun testGetSetClassFieldMultiply() {
        val code = """
            class Vector(var x: Int, var y: Int)
            fun calculate(v: Vector): Int {
                v.x *= 2
                return v.x + v.y
            }
            val tested = calculate(Vector(3,4))
            
            package zauber
            class Int {
                external operator fun times(other: Int): Int
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(10, value.castToInt())
    }

    @Test
    fun testTrySetImmutableField() {
        assertThrows<IllegalStateException>({ e ->
            val message = e.message!!
            check("Expected Field(" in message)
            check(".v).x to be mutable" in message)
        }) {
            val code = """
            class Vector(val x: Int, val y: Int)
            fun calculate(v: Vector): Int {
                v.x = 1
                return v.x + v.y
            }
            val tested = calculate(Vector(3,4))
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
            testExecute(code)
        }
    }
}