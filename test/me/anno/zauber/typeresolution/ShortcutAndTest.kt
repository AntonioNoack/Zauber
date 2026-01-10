package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Test

class ShortcutAndTest {
    // why ever, we get a StackOverflow error for lots of these :/
    @Test
    fun testShortcutAnd() {
        testTypeResolution(
            """
            class X(val x: Int, val y: Float) {
                override fun equals(other: Any?): Boolean {
                    return other is X && other.x == x && other.y == y
                }
            }
            
            val tested = X(0,1f).equals(1)
            
            package zauber
            class Int
            """.trimIndent()
        )
    }
}