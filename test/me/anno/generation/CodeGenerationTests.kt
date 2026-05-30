package me.anno.generation

import me.anno.compilation.MinimalCompiler
import me.anno.generation.InheritanceTable.Companion.inheritanceCode
import me.anno.generation.java.JavaSourceGenerator.Companion.nativeJavaNumbers
import me.anno.utils.assertEquals
import me.anno.zauber.types.Types

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
            external class Int(val content: Int) {
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
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun set(i: Int, value: V)
        }
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
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun set(i: Int, value: V)
        }
        """.trimIndent()

        // todo bug: if we use
        //  operator fun inc() = this + 1
        //  without :Int, we somehow get nothing as the type??

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("21\n", printed)
    }

    fun testIntArrayImpl() {
        // todo add this to test graph-to-class: if (i <= 2) return i
        val code = """
        fun fib(i: Int): Int {
            if (i <= 2) return i
            val v = Array<Int>(i+1)
            v[0] = 1
            v[1] = 1
            var j = 2
            while (j <= i) {
                v[j] = v[j-1] + v[j-2]
                j++
            }
            return v[i]
        }
        fun main() {
            println(fib(7))
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc(): Int = this + 1
        }
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
        }
        external fun println(arg0: Int)
        """.trimIndent()

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("21\n", printed)
    }

    fun testReferenceArrayImpl() {
        val code = """
        class V(val x: Int) {
            operator fun plus(other: V): V = V(this.x + other.x)
        }
        fun fib(i: Int): Int {
            if (i <= 2) return i
            val v = Array<V>(i+1)
            v[0] = V(1)
            v[1] = V(1)
            var j = 2
            while (j <= i) {
                v[j] = v[j-1] + v[j-2]
                j++
            }
            return v[i].x
        }
        fun main() {
            println(fib(7))
        }
        package zauber
        class Any
        external class Int(val content: Int) {
            external operator fun plus(other: Int): Int
            external operator fun minus(other: Int): Int
            external operator fun compareTo(other: Int): Int
            operator fun inc(): Int = this + 1
        }
        enum class Boolean { TRUE, FALSE }
        class Array<V>(val size: Int) {
            external operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
        }
        external fun println(arg0: Int)
        """.trimIndent()

        // 1, 1, 2, 3, 5, 8, 13, 21
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("21\n", printed)
    }

    fun testClassInheritanceImpl() {
        // todo bug: why is in C++ the result type of calc() without hint resolved to Nothing??
        val code = """
            open class Parent {
                open fun calc(): Int = 1
            }
            class Child : Parent() {
                override fun calc(): Int = 2
            }
            fun main() {
                var p: Parent = Child()
                println(p.calc())
            }
            package zauber
            class Any
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                external operator fun compareTo(other: Int): Int
                external operator fun equals(other: Int): Boolean
            }
            external fun println(arg0: Int)
        """.trimIndent() + inheritanceCode

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("2\n", printed)
    }

    fun testInterfaceInheritanceImpl() {
        val code = """
            interface Parent {
                fun calc(): Int = 1
            }
            class Child : Parent {
                override fun calc(): Int = 2
            }
            fun main() {
                var p: Parent = Child()
                println(p.calc())
            }
            package zauber
            class Any
            class Int {
                external operator fun plus(other: Int): Int
                external operator fun times(other: Int): Int
                external operator fun compareTo(other: Int): Int
                external operator fun equals(other: Int): Boolean
            }
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("2\n", printed)
    }

    fun testNumberOverflowsImpl() {

        // todo implement and test automatic widening,
        //  half -> float -> double,
        //  byte -> short -> int -> long,
        //  ubyte -> ushort -> uint -> ulong

        // todo some languages need to escape these names :D, e.g. Java
        val code: String = """
            fun main() {
                val byte: Byte = ${Byte.MAX_VALUE}
                val short: Short = ${Short.MAX_VALUE}
                val int: Int = ${Int.MAX_VALUE}
                val long: Long = ${Long.MAX_VALUE}
                
                println(byte + byte)
                println(short + short)
                println(int + int)
                println(long + long)
                
                val ubyte: UByte = ${UByte.MAX_VALUE}
                val ushort: UShort = ${UShort.MAX_VALUE}
                val uint: UInt = ${UInt.MAX_VALUE}
                val ulong: ULong = ${ULong.MAX_VALUE}
                println(ubyte)
                println(ushort)
                println(uint)
                println(ulong)
            }
            
            package zauber
            class Any
            class Byte {
                external operator fun plus(other: Byte): Byte
            }
            class Short {
                external operator fun plus(other: Short): Short
            }
            class Int {
                external operator fun plus(other: Int): Int
            }
            class Long {
                external operator fun plus(other: Long): Long
            }
            
            class UByte
            class UShort
            class UInt
            class ULong
            
            external fun println(arg0: Byte)
            external fun println(arg0: Short)
            external fun println(arg0: Int)
            external fun println(arg0: Long)
            
            external fun println(arg0: UByte)
            external fun println(arg0: UShort)
            external fun println(arg0: UInt)
            external fun println(arg0: ULong)
        """.trimIndent()
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals(
            listOf(
                (Byte.MAX_VALUE * 2).toByte(),
                (Short.MAX_VALUE * 2).toShort(),
                (Int.MAX_VALUE * 2),
                (Long.MAX_VALUE * 2),
                UByte.MAX_VALUE,
                UShort.MAX_VALUE,
                UInt.MAX_VALUE,
                ULong.MAX_VALUE,
            ).joinToString("") { "$it\n" }, printed
        )
    }

    fun testNumberConversionsImpl() {
        // todo test all number conversions somehow...
        val code: String = TODO()
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("2\n", printed)
    }

    fun testNumberComparisonsImpl() {
        var ctr = 0
        val types = nativeJavaNumbers.keys
        val runnableCode = types.joinToString("") { type ->
            val min = when (type) {
                Types.Char -> "' '"
                Types.Float -> "0f"
                Types.Double -> "0.0"
                else -> "0"
            }
            val max = when (type) {
                // if comparison is signed for unsigned numbers,
                // UNSIGNED.MAX_VALUE is -1, and would give the wrong answer
                Types.Byte -> "${Byte.MAX_VALUE}"
                Types.UByte -> "${UByte.MAX_VALUE}"
                Types.Short -> "${Short.MAX_VALUE}"
                Types.UShort -> "${UShort.MAX_VALUE}"
                Types.Int -> "${Int.MAX_VALUE}"
                Types.UInt -> "${UInt.MAX_VALUE}"
                Types.Long -> "${Long.MAX_VALUE}"
                Types.ULong -> "${ULong.MAX_VALUE}"
                Types.Half -> "65e3h"
                Types.Float -> "1e38f"
                Types.Double -> "1e308"
                Types.Char -> "${UShort.MAX_VALUE}"
                else -> throw NotImplementedError("Max for $type")
            }
            val type = type.clazz.name
            "" +
                    "val v${ctr++}: $type = $min\n" +
                    "val v${ctr++}: $type = $max\n" +
                    "println(if(v${ctr - 2} < v${ctr - 1}) 1 else 0)\n"
        }
        val numberClasses = types.joinToString("") { type ->
            val type = type.clazz.name
            "" +
                    "external class $type(val content: $type) {\n" +
                    "   external fun compareTo(other: $type): Int\n" +
                    "}\n" +
                    "external fun println(arg0: $type)"
        }
        val code: String = """
            fun main() {
                $runnableCode
            }
            
            package zauber
            $numberClasses
        """.trimIndent()
        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("1\n".repeat(types.size), printed)
    }

    fun testNonNumberComparisonsImpl() {
        val code = """
            class Vector(val x: Int, val y: Int) {
                operator fun compareTo(other: Vector): Int {
                    val dx = x.compareTo(other.x)
                    if (dx == 0) return y.compareTo(other.y)
                    return dx
                }
            }
            fun main() {
                val condition = Vector(1, 2) > Vector(1, 3)
                println(if(condition) 17 else 12)
            }
            package zauber
            class Any
            class Int(val content: Int) {
                external operator fun compareTo(other: Int): Int
                external operator fun equals(other: Int): Boolean
            }
            enum class Boolean { TRUE, FALSE }
            class Array<V>(val size: Int) {
                external operator fun set(index: Int, value: V)
            }
            external fun println(arg0: Int)
        """.trimIndent()

        val printed = generator()
            .testCompileMainAndRun(code, ::registerLib)
        assertEquals("12\n", printed)
    }

}