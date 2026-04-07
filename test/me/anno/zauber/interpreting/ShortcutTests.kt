package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortcutTests {

    private val stdlib = """
package zauber
class Throwable()
enum class Boolean { TRUE, FALSE }
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
}
    """.trimIndent()

    @Test
    fun testShortcutAnd() {
        val code = "val tested = false && throw Throwable()\n$stdlib"
        val value = testExecute(code)
        assertEquals(false, value.castToBool())
    }

    @Test
    fun testShortcutOr() {
        val code = "val tested = true || throw Throwable()\n$stdlib"
        val value = testExecute(code)
        assertEquals(true, value.castToBool())
    }
}