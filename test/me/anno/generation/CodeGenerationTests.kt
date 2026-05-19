package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.utils.assertEquals

/**
 * execution time: 2s,
 * main cost is loading Node via NVM, I think
 * */
abstract class CodeGenerationTests {

    abstract fun registerLib()
    abstract fun generator(): MinimalCompiler

    fun testSimpleMathImpl() {
        val code = """
            val x = 5 + 12 / 4
            fun main() {
                println(x)
            }
            
            package zauber
            class Any
            object Unit
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun div(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("8\n", printed)
    }

    fun testMethodCallImpl() {
        val code = """
            fun calc(x: Int) = x+1
            
            fun main() {
                println(calc(2))
            }
            
            package zauber
            class Any
            object Unit
            external class Int {
                external operator fun plus(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("3\n", printed)
    }

    fun testDataClassAndAllocationImpl() {
        val code = """
            data class Vector(val x: Int, val y: Int, val z: Int)
            
            fun main() {
                println(Vector(1, 2, 3).hashCode())
            }
            
            package zauber
            class Any
            object Unit
            external class Int(val content: Int) {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                fun hashCode(): Int = content
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("${(1 * 31 + 2) * 31 + 3}\n", printed)
    }

    fun testGenericClassImpl() {
        val code = """
            class Vector<V>(val x: V)
            
            fun main() {
                println(Vector(1).x)
            }
            
            package zauber
            class Any
            object Unit
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("1\n", printed)
    }

    fun testValueClassFieldIsWritableImpl() {
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
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("7\n", printed)
    }

    fun testValueIsPassedByCopyImpl() {
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
            external class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
            }
            
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("1\n", printed)
    }

    fun testSimpleBranchImpl() {
        val code = """
        fun fib(i: Int): Int {
            if (i <= 2) return i
            return fib(i-1) + fib(i-2)
        }
        fun main() {
            println(fib(7))
        }
        package zauber
        class Any
        external class Int {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc() = this + 1
        }
        external fun println(arg0: Int)
        """.trimIndent()

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("21\n", printed)
    }

    fun testSimpleLoopImpl() {
        val code = """
        fun fib(i: Int): Int {
            var a = 1
            var b = 0
            var k = 0
            while (k <= i) {
                val tmp = a
                a = a + b
                b = tmp
                k++
            }
            return b
        }
        fun main() {
            println(fib(7))
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc(): Int = this + 1
        }
        external fun println(arg0: Int)
        """.trimIndent()

        // todo bug: if we use
        //  operator fun inc() = this + 1
        //  without :Int, we somehow get nothing as the type??

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("21\n", printed)
    }

}