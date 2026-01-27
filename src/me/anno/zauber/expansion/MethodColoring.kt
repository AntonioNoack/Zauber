package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.controlflow.YieldExpression
import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

abstract class MethodColoring<Color : Any> {

    private val cache = HashMap<MethodLike, RecursiveLazy<Color>>()

    operator fun get(method: MethodLike): Color {
        return cache.getOrPut(method) {
            RecursiveLazy { isColoredImpl(method) }
        }.value
    }

    private fun isColoredImpl(method: MethodLike): Color {
        val selfColor = getSelfColor(method)
        val dependencies = getDependencies(method)
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

    fun getDependencies(method: MethodLike): List<MethodLike> =
        getMethodDependencies(method)

    abstract fun getSelfColor(method: MethodLike): Color
    abstract fun mergeColors(self: Color, colors: List<Color>, isRecursive: Boolean): Color

    companion object {
        fun getMethodDependencies(method: MethodLike): List<MethodLike> {
            // check for method calls...
            // todo only the specialized body should be processed...
            val body = method.body ?: return emptyList()
            val result = ArrayList<MethodLike>()
            body.forEachExpressionRecursively{ expr ->
                when (expr) {
                    is YieldExpression, is ReturnExpression, is ThrowExpression -> {}
                    else -> throw NotImplementedError("IsMethodYielding(${expr.javaClass.simpleName})")
                }
            }
            return result.distinct()
        }
    }
}