package me.anno.zauber.interpreting

import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecuteCatch
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsContains
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.impl.ClassType
import org.junit.jupiter.api.Test

class TryCatchTests {

    @Test
    fun testTryCatchNormal() {
        val code = """
            val tested = try {
                1
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testTryCatchMismatch() {
        val code = """
            val tested get() = try {
                throw IllegalArgumentException("")
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecuteCatch(code)
        check(value.type == ReturnType.THROW)
        val type = value.value.clazz.type as ClassType
        check(type.clazz.name == "IllegalArgumentException")
    }

    @Test
    fun testTryCatchCatching() {
        val code = """
            val tested = try {
                throw NullPointerException("")
            } catch(e: NullPointerException) {
                2   
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testLateinitUninitialized() {
        val code = """
            lateinit var likeNull: Int
            val tested: Int get() = try {
                likeNull
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testNullPointerExceptionManual() {
        val code = """
            val likeNull: Int? = null
            val tested = try {
                likeNull ?: throw NullPointerException("Missing likeNull")
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testNullPointerExceptionByNPECall() {
        val code = """
            val likeNull: Int? = null
            val tested = try {
                likeNull!!
            } catch(e: NullPointerException) {
                2
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testSimpleFinallyIsExecuted() {
        val code = """
            val tested = try {
                "Test"
            } finally {
                println("Hello World")
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("Hello World\n", runtime.printed.toString())
    }

    @Test
    fun testCascadedFinallyIsExecuted() {
        val code = """
            val tested = try {
                try {
                    "Test"
                } finally {
                    println("Hello ")
                }
            } finally {
                println("World")
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("Hello \nWorld\n", runtime.printed.toString())
    }

    @Test
    fun testLoopedFinallyIsExecuted() {
        val code = """
            val tested: String get() {
                for (i in 0 until 4) {
                    try {} finally {
                        println(i)
                    }
                }
                return "Test"
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Test", value.castToString())
        assertEquals("0\n1\n2\n3\n", runtime.printed.toString())
    }

    @Test
    fun testReturnInsideFinally() {
        assertThrowsContains<IllegalStateException>("Finally-block for return must not return itself") {
            val code = """
        fun test(): Int {
            try {
                return 1
            } finally {
                return 2
            }
        }
        val tested = test()
        """.trimIndent()
            testExecute(code)
        }
    }

    @Test
    fun testTryWithResource() {
        val code = """
        class Custom {
            fun close() {
                println("Closed")
            }
        }
        class Another {
            fun close() {
                println("Another")
            }
        }
        fun test(): Int {
            try (val a = Custom(), val b = Another()) {
                return 1
            }
        }
        val tested = test()
        """.trimIndent()

        val value = testExecute(code)
        assertEquals(1, value.castToInt())
        assertEquals("Closed\nAnother\n", runtime.printed.toString())
    }

}