package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeAliasExtensionTest {
    @Test
    fun testSimpleTypeAlias() {
        assertEquals(
            IntType,
            TypeResolutionTest.Companion.testTypeResolution(
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
            IntType,
            TypeResolutionTest.Companion.testTypeResolution(
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
            IntType,
            TypeResolutionTest.Companion.testTypeResolution(
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