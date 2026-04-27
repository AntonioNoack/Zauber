package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortcutTest {
    // why ever, we get a StackOverflow error for lots of these :/
    @Test
    fun testShortcutAnd() {
        val actual = testTypeResolution(
            """
            class X(val x: Int, val y: Float) {
                override fun equals(other: Any?): Boolean {
                    return other is X && other.x == x && other.y == y
                }
            }
            
            val tested = X(0,1f).equals(1)
            
            package zauber
            class Int
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Boolean, actual)
    }

    @Test
    fun testShortcutOr() {
        val actual = testTypeResolution(
            """
            class X(val x: Int, val y: Float) {
                override fun equals(other: Any?): Boolean {
                    return !(other !is X || other.x != x || other.y != y)
                }
            }
            
            val tested = X(0,1f).equals(1)
            
            package zauber
            class Int
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Boolean, actual)
    }
}