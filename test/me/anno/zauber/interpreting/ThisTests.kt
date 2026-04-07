package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThisTests {

    @Test
    fun testSimpleField() {
        val code = """
            class A(val x: Int)
            val tested = A(1).x
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testSimpleMethod() {
        val code = """
            class A(val x: Int) {
                fun x() = x
            }
            val tested = A(1).x()
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(1, value.castToInt())
    }

    @Test
    fun testFieldInRunnable() {
        val code = """
            class A(val x: Int)
            val tested = A(2).run { x }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testMethodInRunnable() {
        val code = """
            class A(val x: Int) {
                fun x() = x
            }
            val tested = A(2).run { x() }
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(2, value.castToInt())
    }

    @Test
    fun testFieldInRunnableWithShadowingInMethod() {
        val code = """
            class A(val x: Int) {
                fun add1(x: Int): Int = A(1).run { x } + x
            }
            val tested = A(2).add1(5)
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testMethodInRunnableWithShadowingInMethod() {
        val code = """
            class A(val x: Int) {
                fun add1(x: Int): Int = A(1).run { x() } + x
                fun x() = x
            }
            val tested = A(2).add1(5)
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testFieldInRunnableWithShadowingInSelf() {
        val code = """
            class A(val x: Int) {
                fun add1(): Int = A(1).run { x } + x
            }
            val tested = A(2).add1()
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
            fun interface Function1<P0, R> {
                fun call(p0: P0): R
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testMethodInRunnableWithShadowingInSelf() {
        val code = """
            class A(val x: Int) {
                fun add1(): Int = A(1).run { x() } + x()
                fun x() = x
            }
            val tested = A(2).add1()
            
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
            inline fun <S, R> S.run(runnable: S.() -> R): R {
                return runnable()
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }

    @Test
    fun testExplicitThisWithInnerClass() {
        // todo implement constructing inner classes from outside...
        val code = """
            class A(val x: Int) {
                inner class B(val x: Int) {
                    fun test() = this@A.x + this.x
                }
            }
            val tested = A(1).B(2).test()
            package zauber
            class Int {
                external fun plus(other: Int): Int
            }
        """.trimIndent()
        val value = testExecute(code)
        assertEquals(3, value.castToInt())
    }


    @Test
    fun testIterateOverList() {
        LogManager.disableLoggers(
            "TypeResolution,MemberResolver," +
                    "ASTSimplifier,Runtime," +
                    "Inheritance,ConstructorResolver,CallExpression," +
                    "MethodResolver,ResolvedMethod," +
                    "FieldResolver,FieldExpression,Field,ResolvedField," +
                    ""
        )

        // todo next issue:
        //  Array is only defined as 'Array' in runtime, but we access 'List'.size,
        //  which causes a runtime error...
        //  -> we probably should save which 'this' we have where...

        val sourceCode = """
val tested: Int get() {
    val data = listOf(1,2,3)
    var sum = 0
    for (value in data) {
        sum += value
    }
    return sum
}

package zauber

interface Iterator<V> {
    fun hasNext(): Boolean
    fun next(): V
}

interface Iterable<V> {
    fun iterator(): Iterator<V>
}

interface List<V>: Iterable<V> {
    val size: Int
    operator fun get(index: Int): V
    override fun iterator(): Iterator<V> = ListIterator<V>(this)
    
    operator fun component1(): V = get(0)
    operator fun component2(): V = get(1)
}

class ListIterator<V>(val list: List<V>): Iterator<V> {
    var index = 0
    fun hasNext() = index < list.size
    fun next(): V = list[index++]
}

fun <V> listOf(vararg v: V) = v
class Array<V>(override val size: Int): List<V> {
    external override operator fun get(index: Int): V
    external operator fun set(index: Int, value: V)
}

class Int {
    external operator fun plus(other: Int): Int
    external operator fun compareTo(other: Int): Int
}
        """.trimIndent()
        assertEquals(6, testExecute(sourceCode).castToInt())
    }

}