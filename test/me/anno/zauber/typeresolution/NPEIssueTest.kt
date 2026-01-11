package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Test

class NPEIssueTest {
    @Test
    fun testNPE() {
        testTypeResolution(
            """
        typealias SomeFloat = Float | Double
        fun <N : SomeFloat> N.reciprocal(): N {
            return 1f / this
        }
        val tested = 1f.reciprocal()
            """.trimIndent()
        )
    }
}