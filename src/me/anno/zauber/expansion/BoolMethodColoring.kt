package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.expansion.MethodColoring.Companion.getMethodDependencies
import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

abstract class BoolMethodColoring(val isRecursionColored: Boolean) {
    private val cache = HashMap<MethodLike, RecursiveLazy<Boolean>>()

    operator fun get(method: MethodLike): Boolean {
        return cache.getOrPut(method) {
            RecursiveLazy { isColoredImpl(method) }
        }.value
    }

    private fun isColoredImpl(method: MethodLike): Boolean {
        if (isColoredBySelf(method)) return true
        val dependencies = getDependencies(method)
        if (method in dependencies) return isRecursionColored // easy early-out
        return dependencies.any { funcI ->
            try {
                get(funcI)
            } catch (_: RecursiveException) {
                isRecursionColored
            }
        }
    }

    fun getDependencies(method: MethodLike): List<MethodLike> =
        getMethodDependencies(method)

    abstract fun isColoredBySelf(method: MethodLike): Boolean
}