package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.RuntimeCast.castToString
import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeferTests {

    @Test
    fun testDefer() {
        LogManager.disableLoggers(
            "MemberResolver,Inheritance,TypeResolution,CallExpression,ConstructorResolver," +
                    "MethodResolver,ResolvedMethod"
        )
        val code = """
            fun run(): String {
                defer println("World")
                println("Hello ")
                return "Test"
            }
            val tested get() = run()
            
            package zauber
            class String
            external fun println(str: String)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("Hello ", "World"), runtime.printed)
    }

    @Test
    fun testErrDeferIsExecuted() {
        LogManager.disableLoggers(
            "MemberResolver,Inheritance,TypeResolution,CallExpression,ConstructorResolver," +
                    "MethodResolver,ResolvedMethod"
        )
        val code = """
            fun run(): String {
                try {
                    errdefer println("World")
                    println("Hello ")
                    throw Exception()
                } catch (e: Exception) {
                    return "Test"
                }
            }
            val tested get() = run()
            
            package zauber
            class String
            class Throwable()
            class Exception(): Throwable()
            external fun println(str: String)
            enum class Boolean { TRUE, FALSE }
            object Unit
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("Hello ", "World"), runtime.printed)
    }

    @Test
    fun testErrDeferIsNotExecutedWithoutThrow() {
        LogManager.disableLoggers(
            "MemberResolver,Inheritance,TypeResolution,CallExpression,ConstructorResolver," +
                    "MethodResolver,ResolvedMethod"
        )
        LogManager.getLogger("Runtime").isDebugEnabled = true
        val code = """
            fun run(): String {
                errdefer println("World")
                println("Hello!")
                return "Test"
            }
            val tested get() = run()
            
            package zauber
            class String
            class Exception: Throwable
            external fun println(str: String)
        """.trimIndent()
        val (runtime, value) = testExecute(code)
        assertEquals("Test", runtime.castToString(value))
        assertEquals(listOf("Hello!"), runtime.printed)
    }

}