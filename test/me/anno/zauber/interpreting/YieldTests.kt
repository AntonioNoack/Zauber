package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Test

class YieldTests {
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
            
            package zauber
            
            sealed interface Yieldable<R, T: Throwable, Y> {}
            
            value class Yielded<R, T: Throwable, Y>(
                val yieldedValue: Y,
                val continueRunning: () -> Yielded<R, T, Y>
            ) : Yieldable<R, T, Y> {}
            value class Thrown<T: Throwable>(val value: T) : Yieldable<Nothing, T, Nothing> {}
            value class Returned<R>(val value: R) : Yieldable<Nothing, T, Nothing> {}
            
            class ArrayList<V>
            interface Function0<R> {
                fun call(): R
            }
            class Any {
                open fun hashCode() = 0
                open fun toString() = ""
                open fun equals(other: Any?): Boolean = other === this
            }
        """.trimIndent()
        val value = testExecute(code)
    }

}