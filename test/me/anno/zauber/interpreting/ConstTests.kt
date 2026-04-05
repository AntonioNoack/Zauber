package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConstTests {

    // how can we test sth is const?
    //  out of order evaluation: anything const can be calculated at comptime
    //  -> todo we could allow criss-cross references

    @Test
    fun testConst() {
        val value = testExecute(
            """
            object A {
                const v0: Int = 17
                const v1: Int = B.v0 + 1
                const v2: Int = B.v1 + 2
            }
            object B {
                const v0: Int = A.v0 + 3
                const v1: Int = A.v1 + 4
                const v2: Int = A.v2 + 5
            }
            const tested = B.v2
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
        )
        assertEquals(17 + 1 + 2 + 3 + 4 + 5, runtime.castToInt(value))
    }
}