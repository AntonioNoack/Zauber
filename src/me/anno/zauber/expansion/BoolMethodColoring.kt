package me.anno.zauber.expansion

import me.anno.utils.RecursiveException
import me.anno.zauber.expansion.MethodColoring.Companion.getMethodDependencies
import me.anno.zauber.types.specialization.MethodSpecialization

abstract class BoolMethodColoring(val isRecursionColored: Boolean) : GraphColoring<MethodSpecialization, Boolean>() {

    abstract fun isColoredBySelf(method: MethodSpecialization): Boolean

    override fun isColoredImpl(key: MethodSpecialization): Boolean {
        if (isColoredBySelf(key)) return true
        val dependencies = getDependencies(key)
        if (isRecursionColored && key in dependencies) {
            // easy early-out
            return true
        }
        return dependencies.any { funcI ->
            if (funcI != key) {
                try {
                    get(funcI)
                } catch (_: RecursiveException) {
                    isRecursionColored
                }
            } else isRecursionColored
        }
    }

    override fun getDependencies(key: MethodSpecialization) = getMethodDependencies(key)

    override fun getSelfColor(key: MethodSpecialization): Boolean =
        throw NotImplementedError()

    override fun mergeColors(key: MethodSpecialization, self: Boolean, colors: List<Boolean>, isRecursive: Boolean): Boolean =
        throw NotImplementedError()

}