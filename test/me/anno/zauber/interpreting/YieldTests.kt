package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Test

class YieldTests {
    companion object {
        val stdlib = "\n" + """
package zauber

enum class Boolean { TRUE, FALSE }
external class Int(val content: Int) {
    external fun compareTo(other: Int): Int
    external fun plus(other: Int): Int
    external fun times(other: Int): Int
    fun inc(): Int = this + 1
}

sealed interface Yieldable<R, T: Throwable, Y> {}

value class Yielded<R, T: Throwable, Y>(
    val yieldedValue: Y,
    val continueRunning: () -> Yieldable<R, T, Y>
) : Yieldable<R, T, Y> {}

value class Thrown<T: Throwable>(val value: T) : Yieldable<Nothing, T, Nothing> {}
value class Returned<R>(val value: R) : Yieldable<R, Nothing, Nothing> {}

interface List<V>(val size: Int) {
    operator fun set(index: Int, value: V)
    operator fun get(index: Int): V
}

fun min(a: Int, b: Int): Int {
    return if (a < b) a else b
}

fun max(a: Int, b: Int): Int {
    return if (a > b) a else b
}

class Array<V>(override val size: Int): List<V> {
    override external operator fun set(index: Int, value: V)
    override external operator fun get(index: Int): V
    
    fun copyOf(newSize: Int): Array<V> {
        var copy = Array<V>(newSize)
        var i = 0
        var minSize = min(size, newSize)
        while (i < minSize) {
            copy[i] = this[i]
            i++
        }
        return copy
    }
}

class ArrayList<V>(): List<V> {
    var content = arrayOf<V>()
    override var size = 0
    override operator fun set(index: Int, value: V) {
        content[index] = value
    }
    override operator fun get(index: Int): V {
        return content[index]
    }
    override operator fun add(value: V) {
        if (size+1 > content.size) content = content.copyOf(max(size * 2, 16))
        content[size++] = value
    }
    operator fun removeAt(index: Int) {
        var i = index + 1
        while (i < size) {
            this[i-1] = this[i]
        }
        size--
    }
}

interface Function0<R> {
    fun call(): R
}
class Any {
    open fun hashCode() = 0
    open fun toString() = ""
    open fun equals(other: Any?): Boolean = other === this
}
external fun currentTimeMillis(): Long
fun <V> arrayOf(vararg vs: V): Array<V> = vs
fun <V> arrayListOf(vararg vs: V): ArrayList<V> {
    val result = ArrayList<V>()
    var i = 0
    while (i < vs.size) {
        result.add(vs[i])
        i++
    }
    return result
}

class Throwable(val message: String?)
class Exception(message: String?): Throwable(message)
class NullPointerException(message: String?) : Exception(message)
fun throwNPE(message: String): Nothing {
   throw NullPointerException(message)
}
    """.trimIndent()
    }

    @Test
    fun testSequenceUsingYield() {
        val code = """
            fun <V> collectYielded(runnable: () -> Unit): List<V> {
                var yielded = async runnable()
                val result = ArrayList<V>()
                while (yielded is Yielded<*,*,V>) {
                    result.add((yielded as Yielded<*,*,V>).yieldedValue)
                    yielded = async yielded.continueRunning()
                }
                if (yielded is Thrown) {
                    throw yielded.thrown;
                }
                return result
            }
            
            val tested = collectYielded<Int> {
                yield 1
                yield 2
                yield 3
            }
        """.trimIndent() + stdlib
        val value = testExecute(code)
    }

    @Test
    fun testConcurrentSleepingWorkersUsingYield() {
        val code = """
            class Sleep(millis: Int) {
                var target = currentTimeMillis() + millis
            }
            
            fun runWork(n: Int) {
                yield Sleep(n * 1000)
                println(5 - n)
            }
            
            fun runWorkers(workers: ArrayList<() -> Unit>) {
                val sleeping = ArrayList<Yielded<Unit,Sleep,Nothing>>()
                while (sleeping.size + workers.size > 0) {
                    // work phase
                    while (workers.size > 0) {
                        val result = async workers[0]()
                        if (result is Yielded<*,Sleep,*>) {
                            sleeping.add(result)
                        } else {
                            workers.removeAt(0)
                        }
                    }
                    
                    // sleep phase
                    var now = currentTimeMillis()
                    for (sleeper in sleeping) {
                        if (sleeper.yieldedValue.target >= now) {
                            workers.add(sleeper.continueRunning)
                            sleeping.remove(sleeper)
                            // no concurrency check -> we're fine
                        }
                    }
                }
                // done
            }
            
            val tested = runWorkers(arrayListOf<() -> Unit>(
                { runWork(1) },
                { runWork(2) },
                { runWork(3) },
            ))
        """.trimIndent() + stdlib
        val value = testExecute(code)
        // todo we expect the following printed: 4,3,2, taking 3s total
    }

}