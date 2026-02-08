package me.anno.zauber.expansion

import me.anno.zauber.expansion.MethodColoring.Companion.getMethodDependencies
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

abstract class BoolMethodColoring(val isRecursionColored: Boolean) {
    private val cache = HashMap<MethodSpecialization, RecursiveLazy<Boolean>>()

    operator fun get(method: MethodSpecialization): Boolean {
        return cache.getOrPut(method) {
            RecursiveLazy { isColoredImpl(method) }
        }.value
    }

    private fun isColoredImpl(method: MethodSpecialization): Boolean {
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

    fun getDependencies(method: MethodSpecialization) =
        getMethodDependencies(method)

    abstract fun isColoredBySelf(method: MethodSpecialization): Boolean
}