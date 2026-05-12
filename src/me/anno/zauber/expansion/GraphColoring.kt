package me.anno.zauber.expansion

import me.anno.utils.RecursiveException
import me.anno.utils.RecursiveLazy
import me.anno.utils.ResetThreadLocal.Companion.threadLocal

/**
 * cached, recursive implementation of certain properties,
 * like whether a method throws, yields, or whether a type contains itself
 * */
abstract class GraphColoring<Key : Any, Color : Any> {

    private val cache by threadLocal { HashMap<Key, RecursiveLazy<Color>>() }

    operator fun get(key: Key): Color {
        return cache.getOrPut(key) {
            RecursiveLazy { isColoredImpl(key) }
        }.value
    }

    open fun isColoredImpl(key: Key): Color {
        val selfColor = getSelfColor(key)
        val dependencies = getDependencies(key)
        var isRecursive = false
        val colors = dependencies.mapNotNull { funcI ->
            try {
                get(funcI)
            } catch (_: RecursiveException) {
                isRecursive = true
                null
            }
        }
        return mergeColors(key, selfColor, colors, isRecursive)
    }

    abstract fun getDependencies(key: Key): Collection<Key>

    abstract fun getSelfColor(key: Key): Color
    abstract fun mergeColors(key: Key, self: Color, colors: List<Color>, isRecursive: Boolean): Color

}