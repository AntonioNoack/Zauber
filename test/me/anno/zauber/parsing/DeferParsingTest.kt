package me.anno.zauber.parsing

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeferParsingTest {

    // todo tests for validating that defer and errdefer are executed in the correct order

    @Test
    fun testDeferParsing() {
        Assertions.assertEquals(
            Types.IntType,
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
        Assertions.assertEquals(
            Types.IntType,
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