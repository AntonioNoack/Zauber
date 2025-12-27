package me.anno.zauber

import me.anno.zauber.typeresolution.TypeResolutionTest
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeParserTest {

    companion object {
        fun String.parseType(): Type {
            return TypeResolutionTest.testTypeResolution(
                """
                val tested: $this
            """.trimIndent()
            )
        }
    }

    @Test
    fun testSimpleType() {
        assertEquals(FloatType, "Float".parseType())
        assertEquals(StringType, "String".parseType())
    }

    @Test
    fun testNullableType() {
        assertEquals(unionTypes(StringType, NullType), "String?".parseType())
        assertEquals(unionTypes(FloatType, NullType), "Float?".parseType())
    }

    @Test
    fun testOnlyNullType() {
        assertEquals(NullType, "Nothing?".parseType())
    }

    @Test
    fun testUnionType() {
        assertEquals(unionTypes(FloatType, StringType), "Float|String".parseType())
    }

    @Test
    fun testAndType() {
        // todo this is effectively Nothing, because a type cannot be both sub-branches of a parent with class-inheritance
        assertEquals(andTypes(FloatType, StringType), "Float&String".parseType())
    }

    @Test
    fun testNotType() {
        assertEquals(FloatType.not(), "!Float".parseType())
    }

    @Test
    fun testBindingForce() {
        // ! must be stronger than &
        // & must be stronger than |
        assertEquals(
            unionTypes(
                andTypes(FloatType.not(), DoubleType.not()),
                andTypes(IntType, LongType)
            ), "!Float&!Double|Int&Long".parseType()
        )
    }
}