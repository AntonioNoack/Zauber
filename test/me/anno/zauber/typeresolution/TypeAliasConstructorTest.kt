package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Test

class TypeAliasConstructorTest {

    // todo test type-aliases in method

    @Test
    fun testSimpleTypeAlias() {
        val actualType = testTypeResolution(
            """
            class A
            typealias B = A
            val tested = B()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "A"){
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
        check(actualType is ClassType && actualType.clazz.name == "A"){
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
        check(actualType is ClassType && actualType.clazz.name == "A"){
            "Expected $actualType to be A"
        }
    }
}