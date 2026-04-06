package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertContains
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConstTests {

    // how can we test sth is const?
    // out of order evaluation: anything const can be calculated at comptime
    // -> we allow criss-cross references

    @Test
    fun testConstWithCrissCrossReferences() {
        val value = testExecute(
            """
            object A {
                // order doesn't matter
                const v2: Int = B.v1 + 2
                const v0: Int = 17
                const v1: Int = B.v0 + 1
            }
            object B {
                // order doesn't matter
                const v1: Int = A.v1 + 4
                const v0: Int = A.v0 + 3
                const v2: Int = A.v2 + 5
            }
            const tested = B.v2
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        )
        assertEquals(17 + 1 + 2 + 3 + 4 + 5, value.castToInt())
    }

    @Test
    fun testCanWriteNonConstValue() {
        val value = testExecute(
            """
            value class Vector(val x: Int, val y: Int)
            var vec = Vector(1,3)
            
            fun calculate(): Int {
                vec.x = 0
                return vec.x + vec.y
            }
            
            const tested = calculate()
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        )
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testCannotWriteConstValue() {
        assertThrowsCheck<IllegalStateException>({ message ->
            assertContains("Expected Field(", message)
            assertContains(".x to be mutable", message)
        }) {
            testExecute(
                """
            value class Vector(val x: Int, val y: Int)
            const vec = Vector(1,3)
            
            fun calculate(): Int {
                vec.x = 0
                return vec.x + vec.y
            }
            
            const tested = calculate()
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
            )
        }
    }

    @Test
    fun testConstMustHaveInitialValue() {
        assertThrowsCheck<IllegalStateException>({ message ->
            assertContains("Const field ", message)
            assertContains(".v0 must have initial value", message)
        }) {
            testExecute(
                """
            const v0: Int
            const tested = 0
        """.trimIndent()
            )
        }
    }

    /**
     * we only allow constants in object-likes, so we can compute all of them at comptime
     * */
    @Test
    fun testConstMustBeInObjectLike() {
        assertThrowsCheck<IllegalStateException>({ message ->
            assertContains("Const fields are only supported in object-likes", message)
        }) {
            testExecute(
                """
            class A {
                const x = 0
            }
            const tested = A.x
        """.trimIndent()
            )
        }
    }

}