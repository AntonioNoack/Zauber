package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertContains
import me.anno.zauber.interpreting.FieldGetSetTest.Companion.assertThrowsMessage
import org.junit.jupiter.api.Test

class InitializationOrderTests {
    @Test
    fun testBrokenInitializationOrder() {
        // todo we could statically detect this, don't we?
        //  if there is fields named like that in our class,
        //  in our initialization, and not parameters,
        //  and they are below us,
        //  we cannot use them
        assertThrowsMessage<IllegalStateException>({ message ->
            assertContains("accessed before initialization", message)
        }) {
            val code = """
            val a = b + 1
            val b = 2
            val tested = a
            
            package zauber
            external class Int {
                external operator fun plus(other: Int): Int
            }
        """.trimIndent()
            testExecute(code)
        }
    }
}