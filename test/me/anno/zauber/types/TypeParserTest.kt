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
        assertEquals(Types.Float, "Float".parseType())
        assertEquals(Types.String, "String".parseType())
    }

    @Test
    fun testNullableType() {
        assertEquals(unionTypes(Types.String, NullType), "String?".parseType())
        assertEquals(unionTypes(Types.Float, NullType), "Float?".parseType())
    }

    @Test
    fun testOnlyNullType() {
        assertEquals(NullType, "Nothing?".parseType())
    }

    @Test
    fun testUnionType() {
        assertEquals(unionTypes(Types.Float, Types.String), "Float|String".parseType())
    }

    @Test
    fun testAndType() {
        // todo this is effectively Nothing, because a type cannot be both sub-branches of a parent with class-inheritance
        assertEquals(andTypes(Types.Float, Types.String), "Float&String".parseType())
    }

    @Test
    fun testNotType() {
        assertEquals(Types.Float.not(), "!Float".parseType())
    }

    @Test
    fun testBindingForce() {
        // ! must be stronger than &
        // & must be stronger than |
        assertEquals(
            unionTypes(
                andTypes(Types.Float.not(), Types.Double.not()),
                andTypes(Types.Int, Types.Long)
            ), "!Float&!Double|Int&Long".parseType()
        )
    }
}