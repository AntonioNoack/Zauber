package me.anno.zauber.typeresolution

import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VarargTest {

    private val arrayOfFloatType = ClassType(ArrayType.clazz, listOf(FloatType))

    @Test
    fun testVarargWithNoParameters() {
        assertEquals(
            arrayOfFloatType,
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf<Float>()
            """.trimIndent()
            )
        )
    }

    @Test
    fun testVarargWithOneParameter() {
        assertEquals(
            arrayOfFloatType,
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f)
            """.trimIndent()
            )
        )
    }

    @Test
    fun testVarargWithSomeParameters() {
        assertEquals(
            arrayOfFloatType,
            TypeResolutionTest.testTypeResolution(
                """
                fun <V> arrayOf(vararg values: V): Array<V>
                val tested = arrayOf(0f,1f,2f,43f)
            """.trimIndent()
            )
        )
    }

}