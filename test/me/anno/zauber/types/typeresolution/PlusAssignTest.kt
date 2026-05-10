package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testMethodBodyResolution
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlusAssignTest {

    // todo also test complex accessor chains...

    private fun runTest(code: String) {
        val result = testMethodBodyResolution(code)
        assertEquals(
            listOf(Types.Unit, Types.Unit),
            result
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
                
                package zauber
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
                
                package zauber
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