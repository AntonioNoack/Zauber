package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Test

class YieldTests {
    companion object {
        val stdlib = "\n" + """
package zauber

sealed interface Yieldable<R, T: Throwable, Y> {}

value class Yielded<R, T: Throwable, Y>(
    val yieldedValue: Y,
    val continueRunning: () -> Yieldable<R, T, Y>
) : Yieldable<R, T, Y> {}

value class Thrown<T: Throwable>(val value: T) : Yieldable<Nothing, T, Nothing> {}
value class Returned<R>(val value: R) : Yieldable<R, Nothing, Nothing> {}

fun min(a: Int, b: Int): Int {
    return if (a < b) a else b
}

fun max(a: Int, b: Int): Int {
    return if (a > b) a else b
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
    operator fun add(value: V) {
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
interface Function1<V0,R> {
    fun call(v0: V0): R
}
external fun currentTimeMillis(): Long
fun <V> arrayListOf(vararg vs: V): ArrayList<V> {
    val result = ArrayList<V>()
    var i = 0
    while (i < vs.size) {
        result.add(vs[i])
        i++
    }
    return result
}
    """.trimIndent()
    }

    @Test
    fun testSequenceUsingYield() {
        // todo bug:
        //  this fails, because yielded isn't respecting the while-condition...
        //  to be fair, the field is reassigned...
        val code = """
            fun <V> collectYielded(runnable: () -> Unit): List<V> {
                var yielded = async runnable()
                val result = ArrayList<V>()
                while (yielded is Yielded<*,*,V>) {
                    result.add(yielded.yieldedValue)
                    yielded = async yielded.continueRunning()
                }
                if (yielded is Thrown) {
                    throw yielded.thrown
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
                        if (result is Yielded<Unit,Sleep,Nothing>) {
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
        LogManager.enable("CallWithNames")
        val value = testExecute(code)
        // todo we expect the following printed: 4,3,2, taking 3s total
    }

}