package me.anno.zauber.parsing

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeferParsingTest {

    // todo tests for validating that defer and errdefer are executed in the correct order

    @Test
    fun testDeferParsing() {
        Assertions.assertEquals(
            Types.Int,
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
            Types.Int,
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