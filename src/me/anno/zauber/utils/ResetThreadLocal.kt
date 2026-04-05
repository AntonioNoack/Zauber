package me.anno.zauber.utils

import java.util.*
import kotlin.reflect.KProperty

class ResetThreadLocal<V>(val generator: () -> V) {

    private val values = WeakHashMap<Thread, V>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return values.getOrPut(Thread.currentThread(), generator)
    }

    init {
        synchronized(registered) {
            registered.add(this)
        }
    }

    fun reset(thread: Thread) {
        values.remove(thread)
    }

    companion object {

        val registered = ArrayList<ResetThreadLocal<*>>()

        fun <V> threadLocal(generator: () -> V): ResetThreadLocal<V> {
            return ResetThreadLocal(generator)
        }

        fun reset() {
            val key = Thread.currentThread()
            for (value in registered) {
                value.reset(key)
            }
        }
    }
}