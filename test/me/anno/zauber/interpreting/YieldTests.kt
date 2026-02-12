package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.TestRuntime.Companion.testExecute
import org.junit.jupiter.api.Test

class YieldTests {
    @Test
    fun testSequenceUsingYield() {
        // todo why can yielded not be resolved???
        //  it should be found in collect-names pass
        val code = """
            fun <V> collectYielded(runnable: () -> Unit): List<V> {
                var yielded = async runnable()
                val result = ArrayList<V>()
                while (yielded is Yielded<*, *, V>) {
                    result.add(yielded.yieldedValue)
                    yielded = async yielded.continueRunning()
                }
                if (yielded is Thrown<*, *, *>) {
                    throw yielded.thrown;
                }
                return result
            }
            
            val tested get() = collectYielded<Int> {
                yield 1
                yield 2
                yield 3
            }
            
            package zauber
            // a little clusterfuck
            sealed interface Yieldable<R, T: Throwable, Y> {}
            value class Yielded<R, T: Throwable, Y>(
                val yieldedValue: Y,
                val continueRunning: () -> Yielded<R, T, Y>
            ) : Yieldable<R, T, Y> {}
            value class Thrown<R, T: Throwable, Y>(val value: T) : Yieldable<R, T, Y> {}
            value class Returned<R, T: Throwable, Y>(val value: R) : Yieldable<R, T, Y> {}
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
        val (runtime, value) = testExecute(code)
    }

}