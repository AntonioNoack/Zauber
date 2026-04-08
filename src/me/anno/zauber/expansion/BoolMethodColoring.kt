package me.anno.zauber.expansion

import me.anno.zauber.expansion.MethodColoring.Companion.getMethodDependencies
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.utils.RecursiveException
import me.anno.utils.RecursiveLazy
import me.anno.utils.ResetThreadLocal.Companion.threadLocal

abstract class BoolMethodColoring(val isRecursionColored: Boolean) {
    private val cache by threadLocal { HashMap<MethodSpecialization, RecursiveLazy<Boolean>>() }

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