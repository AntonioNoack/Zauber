package me.anno.generation.java

import me.anno.generation.java.JavaSourceGenerator.register
import me.anno.generation.java.MinimalJavaCompiler.testCompileMainToJavaAndRun
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerateAndRunTest {

    @Test
    fun testSimpleAddition() {
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

    @Test
    fun testMethodCall() {
        // todo bug: ThisExpression -> field isn't set correctly...
        //  for methods, we must create a pseudo-instance,
        //  or resolve these properly -> is difficult with shadowed fields -> we'd need to renamed all these :/
        val code = """
            fun calc(x: Int) = x+1
            
            fun main() {
                println(calc(2))
            }
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = testCompileMainToJavaAndRun(code, true) {
            register(
                langScope, "println", listOf(Types.Int),
                "System.out.println(arg0)"
            )
        }
        assertEquals("3\n", printed)
    }

    @Test
    fun testDataClassAndAllocation() {
        val code = """
            data class Vector(val x: Int, val y: Int, val z: Int)
            
            fun main() {
                println(Vector(1, 2, 3).hashCode())
            }
            
            package zauber
            class Any
            object Unit
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = testCompileMainToJavaAndRun(code, true) {
            register(
                langScope, "println", listOf(Types.Int),
                "System.out.println(arg0)"
            )
        }
        assertEquals("${(1 * 31 + 2) * 31 + 3}\n", printed)

    }

}