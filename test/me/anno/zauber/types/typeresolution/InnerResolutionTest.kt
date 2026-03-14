package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Test

class InnerResolutionTest {
    @Test
    fun testResolvingMethodFromInsideInnerClass() {
        TypeResolutionTest.Companion.testTypeResolution(
            """
            interface Calculator {
                fun calculate(): Int
            }
            class X(val base: Int) {
                fun create(val other: Int): Calculator {
                    return object: Calculator {
                        override fun calculate(): Int {
                            return base * other
                        }
                    }
                }
            }
            
            val tested = X(0).create(1)
            """.trimIndent()
        )
    }
}