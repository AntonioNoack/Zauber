package me.anno.zauber.interpreting

import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Test

class DeferTests {

    @Test
    fun testDefer() {
        val code = """
            fun run(): String {
                defer println("World")
                println("Hello ")
                return "Test"
            }
            val tested = run()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("Hello \nWorld\n", runtime.printed.toString())
    }

    @Test
    fun testErrDeferIsExecuted() {
        val code = """
            fun run(): String {
                try {
                    errdefer println("World")
                    println("Hello ")
                    throw Exception("")
                } catch (e: Exception) {
                    return "Test"
                }
            }
            val tested = run()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("Hello \nWorld\n", runtime.printed.toString())
    }

    @Test
    fun testErrDeferIsNotExecutedWithoutThrow() {
        val code = """
            fun run(): String {
                errdefer println("World")
                println("Hello!")
                return "Test"
            }
            val tested = run()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("Hello!\n", runtime.printed.toString())
    }

}