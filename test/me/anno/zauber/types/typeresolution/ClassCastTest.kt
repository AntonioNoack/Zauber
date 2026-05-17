package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassCastTest {
    @Test
    fun testTypeIsCastInBranch() {
        val actual = testTypeResolution(
            """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = if (x == null) "Test" else x + 1f
                
                // ensure these are registered as classes
                package zauber
                external class Int
                external class Float
                class String
            """.trimIndent(), reset = true
        )
        assertEquals(unionTypes(Types.Float, Types.String), actual)
    }

    @Test
    fun testTypeIsCastAfterReturningBranch() {
        // can we somehow test this?? we need to resolve the x+1f inside the getter...
        //  -> yes, if it compiles, all is fine...
        assertEquals(
            unionTypes(Types.Float, NullType),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested: Float? get() {
                    if (x == null) return null
                    return x+1f
                }
            """.trimIndent()
            )
        )
    }
}