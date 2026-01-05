package me.anno.zauber.typeresolution

import me.anno.zauber.types.Types.UnitType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlusAssignTest {

    // todo also test complex accessor chains...

    private fun runTest(code: String) {
        assertEquals(
            listOf(UnitType, UnitType),
            TypeResolutionTest.testMethodBodyResolution(code)
        )
    }

    @Test
    fun testInstanceIsMutable() {
        runTest(
            """
                class Mutable {
                    operator fun plusAssign(other: Int)
                }
                fun tested() {
                    val instance = Mutable()
                    instance += 1
                }
                val tested = 0
            """.trimIndent()
        )
    }

    @Test
    fun testFieldIsMutable() {
        runTest(
            """
                class Immutable {
                    operator fun plus(other: Int): Immutable
                }
                fun tested() {
                    var instance = Immutable()
                    instance += 1
                }
                val tested = 0
            """.trimIndent()
        )
    }

    @Test
    fun testBothAreMutable() {
        assertThrows<IllegalStateException> {
            runTest(
                """
                class Mutable {
                    operator fun plusAssign(other: Int)
                }
                fun tested() {
                    var instance = Mutable()
                    instance += 1
                }
            """.trimIndent()
            )
        }
    }

    @Test
    fun testBothAreImmutable() {
        assertThrows<IllegalStateException> {
            runTest(
                """
                class Immutable {
                    operator fun plus(other: Int)
                }
                fun tested() {
                    val instance = Immutable()
                    instance += 1
                }
            """.trimIndent()
            )
        }
    }

}