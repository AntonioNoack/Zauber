package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.utils.ResolutionUtils.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeAliasExtensionTest {
    @Test
    fun testSimpleTypeAlias() {
        assertEquals(
            Types.Int,
            testTypeResolution(
                """
            class A
            typealias B = A
            val B.next get() = 0
            val tested = B().next
        """.trimIndent()
            )
        )
    }

    @Test
    fun testSimpleTypeAliasRev() {
        assertEquals(
            Types.Int,
            testTypeResolution(
                """
            val B.next get() = 0
            typealias B = A
            class A
            val tested = B().next
        """.trimIndent()
            )
        )
    }

    @Test
    fun testTypeRecursive() {
        assertEquals(
            Types.Int,
            testTypeResolution(
                """
            val C.next get() = 0
            typealias D = C
            typealias C = B
            val tested = D().next
            typealias B = A
            class A
        """.trimIndent()
            )
        )
    }
}