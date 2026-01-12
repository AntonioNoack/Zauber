package me.anno.zauber.expansion

import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

// todo we also need to collect, which error types a function can throw...
//  or make it explicit...

abstract class MethodColoring<Method, Color : Any> {
    private val cache = HashMap<Method, RecursiveLazy<Color>>()

    fun isColored(method: Method): Color {
        return cache.getOrPut(method) {
            RecursiveLazy { isColoredImpl(method) }
        }.value
    }

    private fun isColoredImpl(method: Method): Color {
        val selfColor = getSelfColor(method)
        val dependencies = getDependencies(method)
        var isRecursive = false
        val colors = dependencies.mapNotNull { funcI ->
            try {
                isColored(funcI)
            } catch (_: RecursiveException) {
                isRecursive = true
                null
            }
        }
        return mergeColors(selfColor, colors, isRecursive)
    }

    abstract fun getDependencies(func: Method): List<Method>
    abstract fun getSelfColor(func: Method): Color
    abstract fun mergeColors(self: Color, colors: List<Color>, isRecursive: Boolean): Color
}