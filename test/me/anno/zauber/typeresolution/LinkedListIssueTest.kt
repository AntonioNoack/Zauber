package me.anno.zauber.typeresolution

import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.getType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkedListIssueTest {
    @Test
    fun testInnerCallResolution() {
        val type = testTypeResolution(
            """
        val tested = LinkedList<Int>(1)
        
        package zauber
        interface Iterator<V> {
            fun hasNext(): Boolean
            fun next(): V
        }

        interface Iterable<V> {
            operator fun iterator(): Iterator<V>
        }
        
        class Array<V>(override val size: Int) : Iterable<V> {
            external override operator fun get(index: Int): V
            external operator fun set(index: Int, value: V)
            external operator fun get(index: Int): V
        }
        
        fun repeat(count: Int, runnable: () -> Unit) {
            var i = count
            while (i > 0) {
                runnable()
                i--
            }
        }
        
        class LinkedList<V>(capacity: Int = 16) : Iterable<V> {
        
            private val content = Array<V>(capacity)
            private val previous = Array<Int>(capacity)
            private val next = Array<Int>(capacity)
        
            var head = -1
            var tail = -1
        
            override fun get(index: Int): V {
                return content[getStorageIndex(index)]
            }
        
            private fun getStorageIndex(index: Int): Int {
                if (index < 0) return -1
                var currIndex = head
                repeat(index) {
                    currIndex = next[currIndex]
                }
                return currIndex
            }
        
            override fun iterator(): Iterator<V> {
                return object : Iterator<V> {
                    var externalIndex = 0
                    var nextIndex = getStorageIndex(externalIndex)
                    var prevIndex = getStorageIndex(externalIndex - 1)
                    override fun hasNext(): Boolean = nextIndex >= 0
                    override fun next(): V {
                        prevIndex = nextIndex
                        nextIndex = next[nextIndex]
                        externalIndex++
                        return content[prevIndex]
                    }
                }
            }
        }
        
        class Int {
            external fun unaryMinus(): Int
            external fun minus(other: Int): Int
        }
            """.trimIndent()
        )
        assertEquals(getType("LinkedList", "V").withTypeParameter(IntType), type)
    }
}