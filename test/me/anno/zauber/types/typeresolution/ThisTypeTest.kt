package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Test

class ThisTypeTest {
    @Test
    fun testSimpleThisType() {
        val actualType = testTypeResolution(
            """
            class A {
                fun setName(): This
            }
            val tested = A().setName()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "A") {
            "Expected $actualType to be A"
        }
    }

    @Test
    fun testThisType() {
        val actualType = testTypeResolution(
            """
            open class A {
                fun setName(): This
            }
            class B(): A()
            val tested = B().setName()
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "B") {
            "Expected $actualType to be B"
        }
    }
}