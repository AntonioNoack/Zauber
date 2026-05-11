package me.anno.generation.cpp

import me.anno.compilation.MinimalCppCompiler
import me.anno.generation.java.JavaSourceGenerator.Companion.register
import me.anno.utils.assertEquals
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Types
import org.junit.jupiter.api.Test

class GenerateAndRunTest {

    private fun registerLib() {
        register(
            langScope, "println", listOf(Types.Int),
            "#include <stdio.h>\n" +
                    "printf(\"%d\\n\",arg0)"
        )
    }

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

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, true, ::registerLib)
        assertEquals("3\n", printed)

    }

    @Test
    fun testMethodCall() {
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

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, ::registerLib)
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
            class Int(val content: Int) {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun hashCode(): Int = content
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("${(1 * 31 + 2) * 31 + 3}\n", printed)
    }

    @Test
    fun testValueClass() {
        // todo how is this fragile???
        val code = """
            value class Vector(val x: Int, val y: Int, val z: Int)
            
            fun main() {
                println(Vector(1, 2, 3).hashCode())
            }
            
            package zauber
            class Any
            object Unit
            
            class Int(val content: Int) {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun hashCode(): Int = content
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        // todo 'this' should work inside hashCode(), too...

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("${(1 * 31 + 2) * 31 + 3}\n", printed)
    }

    @Test
    fun testGenericClass() {
        val code = """
            class Vector<V>(val x: V)
            
            fun main() {
                println(Vector(1).x)
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

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, true, ::registerLib)
        assertEquals("1\n", printed)
    }

    @Test
    fun testValueClassFieldIsWritable() {
        val code = """
            value class Vector(val x: Int, val y: Int, val z: Int)
            
            fun main() {
                var v = Vector(1,2,3)
                v.x += v.y * v.z
                println(v.x)
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

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, true, ::registerLib)
        assertEquals("7\n", printed)
    }

    @Test
    fun testValueIsPassedByCopy() {
        val code = """
            value class Vector(val x: Int)
            
            fun dontModify(v: Vector) {
                var w = v
                w.x = 0
            }
            
            fun main() {
                val v = Vector(1)
                dontModify(v)
                println(v.x)
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

        val printed = MinimalCppCompiler()
            .testCompileMainAndRun(code, true, ::registerLib)
        assertEquals("1\n", printed) {
            "Expected response to be 1, got ${printed.trim()}"
        }
    }

    // todo implement and test working with strings
    // todo test specialized classes being usable

}