package me.anno.zauber.expansion

import me.anno.utils.RecursiveException
import me.anno.utils.RecursiveLazy
import me.anno.utils.ResetThreadLocal.Companion.threadLocal

abstract class MethodOrClassColoring<Color : Any> {

    private val cache by threadLocal { HashMap<MethodOrClassSpecialization, RecursiveLazy<Color>>() }

    operator fun get(moc: MethodOrClassSpecialization): Color {
        return cache.getOrPut(moc) {
            RecursiveLazy { isColoredImpl(moc) }
        }.value
    }

    private fun isColoredImpl(moc: MethodOrClassSpecialization): Color {
        val selfColor = getSelfColor(moc)
        val dependencies = getDependencies(moc)
        var isRecursive = false
        val colors = dependencies.mapNotNull { funcI ->
            try {
                get(funcI)
            } catch (_: RecursiveException) {
                isRecursive = true
                null
            }
        }
        return mergeColors(selfColor, colors, isRecursive)
    }

    abstract fun getDependencies(moc: MethodOrClassSpecialization): Collection<MethodOrClassSpecialization>

    abstract fun getSelfColor(moc: MethodOrClassSpecialization): Color
    abstract fun mergeColors(self: Color, colors: List<Color>, isRecursive: Boolean): Color

}