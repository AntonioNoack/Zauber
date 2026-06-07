package me.anno.zauber.types.typeresolution

import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsMessage
import me.anno.zauber.types.Types
import me.anno.utils.ResolutionUtils.testTypeResolution
import me.anno.utils.StringStyles
import me.anno.zauber.types.impl.ClassType
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class InnerClassTests {

    @Test
    fun testInnerClassCanAccessOuterClass() {
        val type = testTypeResolution(
            """
                class X {
                    var x = 0f
                    inner class I {
                        fun call() = x
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Float, type)
    }

    @Test
    fun testInnerClassConstructor() {
        // todo why does this test fail when used together with others???
        val type = testTypeResolution(
            """
                class X {
                    inner class I {
                        fun call(): Float
                    }
                }
                
                val tested = X().I().call()
            """.trimIndent(), reset = true
        )
        assertEquals(Types.Float, type)
    }

    @Test
    fun testInnerClassConstructorUnavailableWithoutInstance() {
        assertThrowsMessage<IllegalStateException>({
            // todo check message
        }) {
            testTypeResolution(
                """
                class X {
                    inner class I {
                        fun call(): Float
                    }
                }
                
                val tested = X.I().call()
            """.trimIndent(), reset = true
            )
        }
    }

    @Test
    fun testInnerTypeHasOuterGenerics() {
        val actualType = testTypeResolution(
            """
            class Outer<A> {
                inner class Inner
                var inner = Inner()
            }
            val tested = Outer<Int>().inner
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "Inner") {
            "Expected $actualType to be Inner"
        }
        me.anno.utils.assertEquals(listOf(Types.Int), actualType.typeParameters?.toList()) {
            "Expected $actualType to have Int type parameter"
        }
        val testScope = actualType.clazz.parent!!.parent!!.pathStr
        me.anno.utils.assertEquals(
            "$testScope.Outer<zauber.Int>.Inner",
            StringStyles.removeStyles(actualType.toString())
        )
    }

    @Test
    fun testInnerTypeHasBothInnerAndOuterGenerics() {
        val actualType = testTypeResolution(
            """
            class Outer<A> {
                inner class Inner<B>
                var inner = Inner<Float>()
            }
            val tested = Outer<Int>().inner
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "Inner") {
            "Expected $actualType to be Inner"
        }
        me.anno.utils.assertEquals(listOf(Types.Int, Types.Float), actualType.typeParameters?.toList()) {
            "Expected $actualType to have Int type parameter"
        }
        val testScope = actualType.clazz.parent!!.parent!!.pathStr
        me.anno.utils.assertEquals(
            "$testScope.Outer<zauber.Int>.Inner<zauber.Float>",
            StringStyles.removeStyles(actualType.toString())
        )
    }

    @Test
    fun testInnerTypeHasAllGenerics() {
        val actualType = testTypeResolution(
            """
            class Outer2<Z> {
                inner class Outer<A> {
                    inner class Inner<B>
                    var inner = Inner<Float>()
                }
                var inner = Outer<Long>()
            }
            val tested = Outer2<Int>().inner.inner
        """.trimIndent()
        )
        check(actualType is ClassType && actualType.clazz.name == "Inner") {
            "Expected $actualType to be Inner"
        }
        me.anno.utils.assertEquals(listOf(Types.Int, Types.Long, Types.Float), actualType.typeParameters?.toList()) {
            "Expected $actualType to have Int type parameter"
        }
        val testScope = actualType.clazz.parent!!.parent!!.pathStr
        me.anno.utils.assertEquals(
            "$testScope.Outer2<zauber.Int>.Outer<zauber.Long>.Inner<zauber.Float>",
            StringStyles.removeStyles(actualType.toString())
        )
    }
}