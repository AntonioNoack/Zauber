package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExprOrNullTest {

    @Test
    fun testExprOrNull() {
        assertEquals(
            unionTypes(FloatType, NullType),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = x?.plus(0f)
                
                package zauber
                class Int
                class Float
            """.trimIndent()
            )
        )
    }

    @Test
    fun testExprOrOther() {
        assertEquals(
            unionTypes(FloatType, DoubleType),
            testTypeResolution(
                """
                fun Int.plus(other: Float): Float
                
                val x: Int? = null
                val tested = x?.plus(0f) ?: 0.0
                
                package zauber
                class Int
                class Float
            """.trimIndent()
            )
        )
    }
}