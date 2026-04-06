package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunApplyTest {
    @Test
    fun testRun() {
        val actualType = testTypeResolution(
            """
                inline fun <V, R> V.run(runnable: V.() -> R): R {
                    return runnable()
                }
                
                class Impl(val x: Int)
                
                val tested = Impl(1).run { x }
            """.trimIndent()
        )
        assertEquals(Types.Int, actualType)
    }

    @Test
    fun testApply() {
        // todo selfType somehow is not put into context...
        val actualType = testTypeResolution(
            """
                inline fun <V> V.apply(runnable: V.() -> Unit): V {
                    runnable()
                    return this
                }
                
                val tested = "Test".apply { println("Hello") }
                
                package zauber
                external fun println(str: String)
                fun interface Function0<R> {
                    fun call(): R
                }
            """.trimIndent()
        )
        assertEquals(Types.String, actualType)
    }

}