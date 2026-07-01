package me.anno.zauber.interpreting

import me.anno.utils.assertEquals
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecuteCatch
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertContains
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

class NullTests {

    @Test
    fun testNullOr() {
        val code = """
            val nullX: Int? = null
            val tested = nullX ?: 0
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
    }

    @Test
    fun testEnsureNotNullWithValue() {
        val code = """
            val nullX: Int? = 0
            val tested = nullX!!
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(0, value.castToInt())
    }

    @Test
    fun testEnsureNotNullWithNull() {
        val code = """
            val nullX: Int? = null
            val tested: Int get() = nullX!!
        """.trimIndent()
        val value = testExecuteCatch(code)
        check(value.type == ReturnType.THROW)
        val thrown = value.value
        assertEquals(runtime.getClass(Types.NullPointerException), thrown.clazz)
        // todo: why is the message field not assigned?
        val messageIndex = thrown.clazz.fields.indexOfFirst { it.name == "message" }
        val messageField = thrown.fields[messageIndex]?.castToString() ?: ""
        assertContains(" cannot be null", messageField)
        assertContains("nullX", messageField)
    }

}