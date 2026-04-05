package me.anno.zauber.types

import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.typeresolution.TypeResolutionTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeParserTest {

    companion object {
        fun String.parseType(): Type {
            return TypeResolutionTest.testTypeResolution("val tested: $this")
        }
    }

    @Test
    fun testSimpleType() {
        assertEquals(Types.FloatType, "Float".parseType())
        assertEquals(Types.StringType, "String".parseType())
    }

    @Test
    fun testNullableType() {
        assertEquals(unionTypes(Types.StringType, NullType), "String?".parseType())
        assertEquals(unionTypes(Types.FloatType, NullType), "Float?".parseType())
    }

    @Test
    fun testOnlyNullType() {
        assertEquals(NullType, "Nothing?".parseType())
    }

    @Test
    fun testUnionType() {
        assertEquals(unionTypes(Types.FloatType, Types.StringType), "Float|String".parseType())
    }

    @Test
    fun testAndType() {
        // todo this is effectively Nothing, because a type cannot be both sub-branches of a parent with class-inheritance
        assertEquals(andTypes(Types.FloatType, Types.StringType), "Float&String".parseType())
    }

    @Test
    fun testNotType() {
        assertEquals(Types.FloatType.not(), "!Float".parseType())
    }

    @Test
    fun testBindingForce() {
        // ! must be stronger than &
        // & must be stronger than |
        assertEquals(
            unionTypes(
                andTypes(Types.FloatType.not(), Types.DoubleType.not()),
                andTypes(Types.IntType, Types.LongType)
            ), "!Float&!Double|Int&Long".parseType()
        )
    }
}