package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.utils.ResolutionUtils.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NPEIssueTest {
    @Test
    fun testNPE() {
        val actual = testTypeResolution(
            """
        typealias SomeFloat = Float | Double
        fun <N : SomeFloat> N.reciprocal(): N {
            return 1f / this
        }
        val tested = 1f.reciprocal()
        
        package zauber
            """.trimIndent()
        )
        assertEquals(Types.Float, actual)
    }
}