package me.anno.zauber

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.Types.IntType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeferParsingTest {
    @Test
    fun testDeferParsing() {
        assertEquals(
            IntType,
            testTypeResolution(
                """
                class Action {
                    fun call() {}
                }
                fun test(): Int {
                    val x = Action()
                    defer x.call()
                    return 0
                }
                val tested = test()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testErrdeferParsing() {
        assertEquals(
            IntType,
            testTypeResolution(
                """
                class Action {
                    fun call() {}
                }
                fun test(): Int {
                    val x = Action()
                    errdefer x.call()
                    return 0
                }
                val tested = test()
            """.trimIndent()
            )
        )
    }
}