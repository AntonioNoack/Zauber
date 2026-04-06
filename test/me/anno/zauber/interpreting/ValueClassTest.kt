package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueClassTest {

    /**
     * when value-class is assigned (set) to another field, a copy must be created
     * */
    @Test
    fun testSettingValueClassFields() {
        val code = """
            value class Vector(val x: Int, val y: Int)
            fun calculate(v: Vector): Int {
                var w = v
                w.x = 1
                return w.x + v.x
            }
            val tested = calculate(Vector(3,0))
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(4, value.castToInt())
    }

    /**
     * While x is usually writable, v is not, so v.x is neither
     * */
    @Test
    fun testTrySetImmutableField() {
        assertThrowsCheck<IllegalStateException>({ message ->
            check("Expected Field(" in message)
            check(".v).x to be mutable" in message)
        }) {
            val code = """
            value class Vector(val x: Int, val y: Int)
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