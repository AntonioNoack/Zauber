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
        val value = testExecute(
            """
                class X {
                    var x = 0f
                    inner class I {
                        fun call() = x
                    }
                }
                
                val tested = X().I().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    @Test
    fun testInnerClassCanAccessOuterClassMethods() {
        LogManager.disableLoggers(
            "" +
                    "TypeResolution,MemberResolver,CallExpression"
        )
        // todo issue: this-chain is not solved properly; are the methods called on the correct instance???
        val value = testExecute(
            """
                class X {
                    var y = 0f
                    fun x() = y
                    inner class I {
                        fun call() = x()
                    }
                }
                
                val tested = X().I().call()
                
                package zauber
                class Any
            """.trimIndent()
        )
        assertEquals(0f, value.castToFloat())
    }

    // todo test that we can access outer class constructors ^^, creating I() from inside I
    // todo test (deeply) nested inner classes -> must create a getter-chain

    @Test
    fun testCallInnerClassConstructorFromOutside() {
        val type = testExecute(
            """
                class X {
                    inner class I {
                        fun call(): Float = 0f
                    }
                }
                
                val tested = X().I().call()
                
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
        // todo bug: FieldExpression doesn't validate 'this.' enough
        val type = testExecute(
            """
                class X(val x: Int) {
                    inner class I {
                        fun call(): Int = x++
                    }
                    fun calc(): Int = I().call()
                }
                
                val tested = X(5).calc()
                
                package zauber
                class Any
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(5, type.castToInt())
    }
}