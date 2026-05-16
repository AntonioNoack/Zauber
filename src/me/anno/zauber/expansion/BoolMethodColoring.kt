package me.anno.zauber.expansion

import me.anno.utils.RecursiveException
import me.anno.zauber.expansion.MethodColoring.Companion.getMethodDependencies
import me.anno.zauber.types.Specialization

abstract class BoolMethodColoring(val isRecursionColored: Boolean) : GraphColoring<Specialization, Boolean>() {

    abstract fun isColoredBySelf(method: Specialization): Boolean

    override fun isColoredImpl(key: Specialization): Boolean {
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

    override fun getDependencies(key: Specialization) = getMethodDependencies(key)

    override fun getSelfColor(key: Specialization): Boolean =
        throw NotImplementedError()

    override fun mergeColors(key: Specialization, self: Boolean, colors: List<Boolean>, isRecursive: Boolean): Boolean =
        throw NotImplementedError()

}