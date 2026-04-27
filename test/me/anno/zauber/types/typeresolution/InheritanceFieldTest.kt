package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InheritanceFieldTest {

    @Test
    fun testDirectField() {
        val actual = testTypeResolution(
            """
                class A {
                    val size: Int
                }
                
                val tested = A().size
            """.trimIndent(),
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testDirectFieldWithGenerics() {
        val actual = testTypeResolution(
            """
                class A<V> {
                    val size: Int
                }
                
                val tested = A<Int>().size
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperFieldX1() {
        val actual = testTypeResolution(
            """
                open class A {
                    val size: Int
                }
                class B: A()
                
                val tested = B().size
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperFieldX1WithGenerics() {
        val actual = testTypeResolution(
            """
                open class A<V> {
                    val size: Int
                }
                class B<X>: A<X>()
                
                val tested = B<Float>().size
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperFieldX2() {
        val actual = testTypeResolution(
            """
                open class A {
                    val size: Int
                }
                open class B: A()
                class C: B()
                
                val tested = C().size
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperFieldX2WithGenerics() {
        val actual = testTypeResolution(
            """
                open class A<I> {
                    val size: Int
                }
                open class B<J>: A<J>()
                class C<K>: B<K>()
                
                val tested = C<Float>().size
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }
}