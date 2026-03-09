package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Test

class ThisTypeTest {
    @Test
    fun testThisType() {
        val actualType =
            _root_ide_package_.me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution(
                """
            open class A {
                fun setName(): This
            }
            class B(): A()
            val tested = B().setName()
        """.trimIndent()
            )
        check(actualType is ClassType && actualType.clazz.name == "B"){
            "Expected $actualType to be B"
        }
    }
}