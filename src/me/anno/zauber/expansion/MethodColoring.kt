package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.*
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.utils.RecursiveException
import me.anno.zauber.utils.RecursiveLazy

abstract class MethodColoring<Color : Any> {

    private val cache = HashMap<MethodSpecialization, RecursiveLazy<Color>>()

    operator fun get(method: MethodSpecialization): Color {
        return cache.getOrPut(method) {
            RecursiveLazy { isColoredImpl(method) }
        }.value
    }

    private fun isColoredImpl(method: MethodSpecialization): Color {
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

    fun getDependencies(method: MethodSpecialization) =
        getMethodDependencies(method)

    abstract fun getSelfColor(method: MethodSpecialization): Color
    abstract fun mergeColors(self: Color, colors: List<Color>, isRecursive: Boolean): Color

    companion object {
        private val LOGGER = LogManager.getLogger(MethodColoring::class)

        fun getMethodDependencies(method: MethodSpecialization): List<MethodSpecialization> {
            // check for method calls...
            val body = method.method.getSpecializedBody(method.specialization) ?: return emptyList()
            val result = ArrayList<MethodSpecialization>()
            body.forEachExpressionRecursively { expr ->
                when (expr) { // only top-level needs to be checked
                    is YieldExpression, is ReturnExpression, is ThrowExpression,
                    is ThisExpression, is NumberExpression, is StringExpression,
                    is IfElseBranch, is WhileLoop, is DoWhileLoop, is ExpressionList -> {
                        // no direct call
                    }
                    is ResolvedCallExpression -> {
                        when (val method1 = expr.callable) {
                            is ResolvedMethod,
                            is ResolvedConstructor ->
                                result.add(MethodSpecialization(method1.resolved, method1.specialization))

                            is ResolvedField -> {
                                TODO("get call-dependencies from call on field")
                            }
                        }
                    }
                    is ResolvedCompareOp -> {
                        val method1 = expr.callable
                        result.add(MethodSpecialization(method1.resolved, method1.specialization))
                    }
                    is ResolvedGetFieldExpression -> {
                        val field = expr.field.resolved
                        val getter = field.getter
                        if (getter != null && field.hasCustomGetter) {
                            result.add(MethodSpecialization(getter, expr.field.specialization))
                        }
                    }
                    is ResolvedSetFieldExpression -> {
                        val field = expr.field.resolved
                        val setter = field.setter
                        if (setter != null && field.hasCustomSetter) {
                            result.add(MethodSpecialization(setter, expr.field.specialization))
                        }
                    }
                    else -> {
                        if (!expr.isResolved()) {
                            LOGGER.warn("Unresolved expr in getMethodDependencies: ${expr.javaClass.simpleName}")
                        } else throw NotImplementedError("IsMethodYielding(${expr.javaClass.simpleName})")
                    }
                }
            }
            return result.distinct()
        }
    }
}