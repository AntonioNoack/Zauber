package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * in inner classes
 * we need to add a synthetic field for the other class,
 * and we must also add it to all constructors, like in enums
 * */
class InnerClassTests {

    @Test
    fun testInnerClassCanAccessOuterClassFields() {
        LogManager.disableLoggers("TypeResolution,MemberResolver,CallExpression")
        val value = testExecute(
            """
                class Outer {
                    var x = 0f
                    inner class Inner {
                        fun call() = x
                    }
                }
                
                val tested = Outer().Inner().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    @Test
    fun testInnerClassCanAccessOuterClassFieldChain() {
        LogManager.disableLoggers("TypeResolution,MemberResolver,CallExpression")
        val value = testExecute(
            """
                class Outer {
                    var x = 0f
                    inner class Inner {
                        val y get() = x
                    }
                }
                
                val tested = Outer().Inner().y
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    @Test
    fun testInnerClassCanAccessOuterClassMethods() {
        LogManager.disableLoggers("TypeResolution,MemberResolver,CallExpression")
        val value = testExecute(
            """
                class Outer {
                    var y = 0f
                    fun x() = y
                    inner class Inner {
                        fun call() = x()
                    }
                }
                
                val tested = Outer().Inner().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    @Test
    fun testCallInnerClassConstructorFromOutside() {
        val type = testExecute(
            """
                class Outer {
                    inner class Inner {
                        fun call(): Float = 0f
                    }
                }
                
                val tested = Outer().Inner().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, type.castToFloat())
    }

    @Test
    fun testCallInnerClassConstructorFromInside() {
        LogManager.disableLoggers(
            "" +
                    "ASTSimplifier,TypeResolution,CallExpression,MemberResolver,Inheritance,Runtime," +
                    "ResolvedMethod,MethodResolver," +
                    "ConstructorResolver," +
                    ""
        )
        val type = testExecute(
            """
                class Outer(var x: Int) {
                    inner class Inner {
                        fun call(): Int = ++x
                    }
                    fun calc(): Int = Inner().call()
                }
                
                val tested = Outer(5).calc()
                
                package zauber
                class Any
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

    @Test
    fun testCallInnerClassConstructorFromInsideInner() {
        LogManager.disableLoggers(
            "" +
                    "ASTSimplifier,TypeResolution,Inheritance,Runtime," +
                    "CallExpression,MemberResolver,SuperCallExpression," +
                    "ResolvedMethod,MethodResolver," +
                    "ConstructorResolver," +
                    "ResolvedField,FieldExpression,FieldResolver"
        )
        val type = testExecute(
            """
                class Outer(var x: Int) {
                    inner class Inner {
                        fun call(): Int = Inner().x
                    }
                }
                
                val tested = Outer(5).Inner().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(5, type.castToInt())
    }


    @Test
    fun testInnerClassChainAndFieldAccess() {
        LogManager.disableLoggers(
            "" +
                    "ASTSimplifier,TypeResolution,Inheritance,Runtime," +
                    "CallExpression,MemberResolver,SuperCallExpression," +
                    "ResolvedMethod,MethodResolver," +
                    "ConstructorResolver," +
                    "ResolvedField,FieldExpression,FieldResolver"
        )
        val type = testExecute(
            """
                class Outer(var x: Int) {
                    inner class I1 {
                        inner class I2 {
                            inner class I3 {
                                fun call(): Int = ++x
                            }
                        }
                    }
                }
                
                val tested = Outer(5).I1().I2().I3().call()
                
                package zauber
                class Any
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc() = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

}