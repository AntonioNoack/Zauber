package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FunInterfaceTest {
    @Test
    fun testFunInterfaceCall() {
        assertEquals(
            Types.BooleanType,
            // todo somehow check that the inside of the functions work, too
            //  (I know they don't because we haven't implemented calls on fun-interfaces yet)
            TypeResolutionTest.testTypeResolution(
                """
                fun interface Condition {
                    fun calculate(value: Int): Boolean
                }
                fun filter(condition: Condition): Boolean {
                    return condition(0)
                }
                val tested = filter { true }
            """.trimIndent()
            )
        )
    }
}