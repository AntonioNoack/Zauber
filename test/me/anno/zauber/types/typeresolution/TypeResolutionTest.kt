package me.anno.zauber.types.typeresolution

import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.utils.ResolutionUtils.testTypeResolutionGetField
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeResolutionTest {

    @Test
    fun testConstants() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = true")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = false")
        )
        assertEquals(
            NullType,
            testTypeResolution("val tested = null")
        )
        assertEquals(
            Types.Int,
            testTypeResolution("val tested = 0")
        )
        assertEquals(
            Types.Long,
            testTypeResolution("val tested = 0L")
        )
        assertEquals(
            Types.Float,
            testTypeResolution("val tested = 0f")
        )
        assertEquals(
            Types.Float,
            testTypeResolution("val tested = 0.0f")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 0d")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 0.0")
        )
        assertEquals(
            Types.Double,
            testTypeResolution("val tested = 1e3")
        )
        assertEquals(
            Types.Char,
            testTypeResolution("val tested = ' '")
        )
        assertEquals(
            Types.String,
            testTypeResolution("val tested = \"Test 123\"")
        )
    }

    @Test
    fun testNullableTypes() {
        val actual = testTypeResolution("val tested: Boolean?")
        assertEquals(UnionType(listOf(Types.Boolean, NullType)), actual)
    }

    @Test
    fun testConstructorWithParameter() {
        val actual = testTypeResolution(
            """
            val tested = IntArray(5)
            
            package zauber
            class Array<V>(val size: Int)
            typealias IntArray = Array<Int>
        """.trimIndent(), true
        )
        assertEquals(Types.Array.withTypeParameter(Types.Int), actual)
    }

    @Test
    fun testGetOperator() {
        val type = testTypeResolution(
            """
            class Node(val value: Int)
            val x: Node
            val tested = x.value
        """.trimIndent()
        )
        assertEquals(Types.Int, type)
    }

    @Test
    fun testIfNullOperator() {
        val type =
            testTypeResolution(
                """
            val x: Int?
            val tested = x ?: 0f
        """.trimIndent()
            )
        assertEquals(unionTypes(Types.Int, Types.Float), type)
    }

    @Test
    fun testNullableGetOperator() {
        val type =
            testTypeResolution(
                """
            class Node(val parent: Node?, val value: Int)
            val x: Node?
            val tested = x?.parent?.value
        """.trimIndent()
            )
        assertEquals(unionTypes(Types.Int, NullType), type)
    }

    @Test
    fun testCompareOperators() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 < 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 <= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 > 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 >= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 == 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 != 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 === 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0 !== 1")
        )
    }

    @Test
    fun testCompareOperatorsMixed() {
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f < 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f <= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f > 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f >= 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f == 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f != 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f === 1")
        )
        assertEquals(
            Types.Boolean,
            testTypeResolution("val tested = 0f !== 1")
        )
    }

    @Test
    fun testValueType() {
        val field = testTypeResolutionGetField("value val tested = \"\"", true)
        check(field.flags.hasFlag(Flags.VALUE))
    }
}