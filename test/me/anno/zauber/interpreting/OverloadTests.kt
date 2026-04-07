package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverloadTests {
    @Test
    fun testMethodOverloads() {
        val code1 = """
            fun f(x: Int) = 1
            fun f(x: Float) = 2
            val tested = f(1)
        """.trimIndent()
        assertEquals(1, testExecute(code1).castToInt())
        val code2 = """
            fun f(x: Int) = 1
            fun f(x: Float) = 2
            val tested = f(1f)
        """.trimIndent()
        assertEquals(2, testExecute(code2).castToInt())
    }
}