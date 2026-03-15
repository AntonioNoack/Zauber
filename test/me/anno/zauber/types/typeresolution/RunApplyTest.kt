package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunApplyTest {
    @Test
    fun testRun() {
        assertEquals(
            IntType,
            testTypeResolution(
                """
                inline fun <V, R> V.run(runnable: V.() -> R): R {
                    return runnable()
                }
                
                class Impl(val x: Int)
                
                val tested = Impl(1).run { x }
            """.trimIndent()
            )
        )
    }

    @Test
    fun testApply() {
        assertEquals(
            StringType,
            testTypeResolution(
                """
                inline fun <V> V.apply(runnable: V.() -> Unit): V {
                    runnable()
                    return this
                }
                
                val tested = "Test".apply { println(size) }
            """.trimIndent()
            )
        )
    }

}