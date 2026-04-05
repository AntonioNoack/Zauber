package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InheritanceInterfaceTests {

    @Test
    fun testCallInterfaceDefaultMethod() {
        LogManager.disableLoggers(
            "CallExpression,ZClass,MemberResolver,ConstructorResolver," +
                    "TypeResolution,Inheritance," +
                    "MethodResolver,ResolvedMethod," +
                    "FieldExpression,FieldResolver,ResolvedField," +
                    "Runtime,ASTSimplifier"
        )
        val code = """
            interface Parent {
                open fun x() = 0
            }
            class Child: Parent
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(Types.IntType, value.type.type)
        assertEquals(0, castToInt(value))
    }

    @Test
    fun testCallInterfaceDefaultField() {
        val code = """
            interface Parent {
                val x get() = 0
            }
            class Child: Parent
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, castToInt(value))
    }

    @Test
    fun testCallInterfaceMethod() {
        val code = """
            interface Parent {
                fun x() = 0
            }
            class Child: Parent {
                override fun x() = 1
            }
            fun runTest(p: Parent): Int = p.x()
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, castToInt(value))
    }

    @Test
    fun testCallInterfaceField() {
        LogManager.disableLoggers(
            "ZClass,TypeResolution,CallExpression,MemberResolver,ConstructorResolver," +
                    "MemberResolver,Inheritance," +
                    "MethodResolver,ResolvedMethod," +
                    "FieldExpression,FieldResolver,ResolvedField,Field," +
                    "Runtime,ASTSimplifier"
        )
        val code = """
            interface Parent {
                val x get() = 0
            }
            class Child: Parent {
                override val x get() = 1
            }
            fun runTest(p: Parent): Int = p.x
            val tested = runTest(Child())
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, castToInt(value))
    }
}