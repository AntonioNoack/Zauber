package me.anno.utils

import java.util.*
import kotlin.reflect.KProperty

class ResetThreadLocal<V : Any>(val generator: () -> V) {

    private val values = WeakHashMap<Thread, V>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    init {
        synchronized(registered) {
            registered.add(this)
        }
    }

    fun reset(thread: Thread) {
        synchronized(values) {
            values.remove(thread)
        }
    }

    val value: V
        get() = synchronized(values) {
            values.getOrPut(Thread.currentThread(), generator)
        }

    companion object {

        val registered = ArrayList<ResetThreadLocal<*>>()

        fun <V : Any> threadLocal(generator: () -> V): ResetThreadLocal<V> {
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