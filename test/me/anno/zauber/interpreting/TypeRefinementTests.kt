package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeRefinementTests {
    @Test
    fun testTypeRefinement() {
        val code = """
            val tested: Int get() {
                val x: Any = 1
                if (x is Int) {
                    return x + 1
                }
                return 0
            }
            
            package zauber
            class Any
            class Int {
                external operator fun plus(other: Int): Int
            }
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
        """.trimIndent()
        assertEquals(2, testExecute(code).castToInt())
    }
}