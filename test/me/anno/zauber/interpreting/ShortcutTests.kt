package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class ShortcutTests {

    @Test
    fun testShortcutAnd() {
        val code = "val tested = false && throw Throwable(\"\")"
        val value = testExecute(code)
        assertEquals(false, value.castToBool())
    }

    @Test
    fun testShortcutOr() {
        val code = "val tested = true || throw Throwable(\"\")"
        val value = testExecute(code)
        assertEquals(true, value.castToBool())
    }
}