package me.anno.generation.java

import me.anno.generation.java.JavaSourceGenerator.register
import me.anno.generation.java.MinimalJavaCompiler.testCompileMainToJavaAndRun
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaGenAndRunTest {

    @Test
    fun testGenerateAndRunSimpleAddition() {
        val code = """
            val x = 1 + 2
            fun main() {
                println(x)
            }
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = testCompileMainToJavaAndRun(code) {
            register(
                langScope, "println", listOf(Types.Int),
                "System.out.println(arg0)"
            )
        }
        assertEquals("3\n", printed)

    }

}