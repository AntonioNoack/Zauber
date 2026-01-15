package me.anno.zauber.expansion

import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

abstract class BoolMethodColoring<F>(val isRecursionColored: Boolean) {
    private val cache = HashMap<F, RecursiveLazy<Boolean>>()

    operator fun get(func: F): Boolean {
        return cache.getOrPut(func) {
            RecursiveLazy { isColoredImpl(func) }
        }.value
    }

    private fun isColoredImpl(func: F): Boolean {
        if (isColoredBySelf(func)) return true
        val dependencies = getDependencies(func)
        if (func in dependencies) return isRecursionColored // easy early-out
        return dependencies.any { funcI ->
            try {
                get(funcI)
            } catch (_: RecursiveException) {
                isRecursionColored
            }
        }
    }

    abstract fun getDependencies(func: F): List<F>
    abstract fun isColoredBySelf(func: F): Boolean
}