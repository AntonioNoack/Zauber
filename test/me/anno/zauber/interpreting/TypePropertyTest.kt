package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

// Kotlin handles types very specifically...
//  we're developing our own language, so let's do better, and handle types as first-class citizens
//  -> that would cause collisions between objects and their reflections properties -> retain ::class

class TypePropertyTest {

    // todo only class-types have a name, but all types have a toString() method

    @Test
    fun testTypeName() {
        // todo bug: ClassType is missing generics somehow...
        //  probably ::class doesn't set them?
        LogManager.enableAllLoggers()
        val code = """
            val tested = Int::class.name
            
            package zauber
            class ClassType<V>(val name: String)
        """.trimIndent()
        val value = testExecute(code)
        assertEquals("Int", value.castToString())
    }
}