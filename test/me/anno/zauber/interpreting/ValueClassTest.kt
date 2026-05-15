package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsContains
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// todo make sure that:
//  value classes can only extend value classes or Any
//  value classes cannot self-reference
// todo should we make Int a value class? theoretically, it is, or something special...
// should we make String a value class? no, too heavy, but we could add ShortString like in C++ (u8x8, \0 = end)

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
            class Any
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
        assertThrowsMessage<IllegalStateException>({ message ->
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
            class Any
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
            testExecute(code)
        }
    }

    @Test
    fun testValueClassMustHavePrimaryConstructor() {
        assertThrowsContains<IllegalStateException>("must have at least one field") {
            val code = """
                value class Vector
                val tested: Int = Vector()
                
                package zauber
                class Any
            """.trimIndent()
            testExecute(code)
        }
    }

    @Test
    fun testValueClassMustHaveAtLeastOneParameter() {
        assertThrowsContains<IllegalStateException>("must have at least one field") {
            val code = """
                value class Vector()
                val tested: Int = Vector()
                
                package zauber
                class Any
            """.trimIndent()
            testExecute(code)
        }
    }

    @Test
    fun testValueClassMustHaveValueParameters() {
        assertThrowsContains<IllegalStateException>("fields must be immutable") {
            val code = """
                value class Vector(var x: Int)
                val tested: Int = Vector(3)
                
                package zauber
                class Any
            """.trimIndent()
            testExecute(code)
        }
    }
}