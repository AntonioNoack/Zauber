package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConstTests {

    // todo how can we test sth is const?

    @Test
    fun testConst() {
        val (rt, value) = testExecute(
            """
            const tested = 1
        """.trimIndent()
        )
        assertEquals(1, rt.castToInt(value))
    }
}