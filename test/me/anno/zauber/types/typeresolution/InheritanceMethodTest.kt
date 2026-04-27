package me.anno.zauber.types.typeresolution

import me.anno.zauber.types.Types
import me.anno.zauber.types.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InheritanceMethodTest {

    @Test
    fun testDirectCall() {
        val actual = testTypeResolution(
            """
                class A {
                    fun call(): Int
                }
                
                val tested = A().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testDirectCallWithGenerics() {
        val actual = testTypeResolution(
            """
                class A<V> {
                    fun call(): Int
                }
                
                val tested = A<Int>().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperCallX1() {
        val actual = testTypeResolution(
            """
                open class A {
                    fun call(): Int
                }
                class B: A()
                
                val tested = B().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperCallX1WithGenerics() {
        val actual = testTypeResolution(
            """
                open class A<V> {
                    fun call(): Int
                }
                class B<X>: A<X>()
                
                val tested = B<Float>().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperCallX2() {
        val actual = testTypeResolution(
            """
                open class A {
                    fun call(): Int
                }
                open class B: A()
                class C: B()
                
                val tested = C().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }

    @Test
    fun testSuperCallX2WithGenerics() {
        val actual = testTypeResolution(
            """
                open class A<I> {
                    fun call(): Int
                }
                open class B<J>: A<J>()
                class C<K>: B<K>()
                
                val tested = C<Float>().call()
            """.trimIndent()
        )
        assertEquals(Types.Int, actual)
    }
}