package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeAliasConstructorTest {

    @Test
    fun testSimpleTypeAlias() {
        val actualType = testTypeResolution(
            """
            class A
            typealias B = A
            val tested = B()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "A") {
            "Expected $actualType to be A"
        }
    }

    @Test
    fun testSimpleTypeAliasRev() {
        val actualType = testTypeResolution(
            """
            typealias B = A
            class A
            val tested = B()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "A") {
            "Expected $actualType to be A"
        }
    }

    @Test
    fun testTypeRecursive() {
        val actualType = testTypeResolution(
            """
            typealias D = C
            typealias C = B
            typealias B = A
            class A
            val tested = D()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "A") {
            "Expected $actualType to be A"
        }
    }

    @Test
    fun testTypeAliasInsideGetter() {
        // todo type is incorrect:
        //  return has type Nothing,
        //  but what we actually want is what is returned,
        //  find that out somehow, I know we can do it...
        // (Kotlin doesn't even allow this)
        val actualType = testTypeResolution(
            """
            val tested get() {
                typealias V = Int
                return arrayOf<V>()
            }
            
            package zauber
            fun <X> arrayOf(vararg v: X): Array<X> = v
        """.trimIndent()
        )
        assertEquals(Types.List.withTypeParameter(Types.Int), actualType)
    }
}