package me.anno.zauber.expansion

import me.anno.generation.Specializations
import me.anno.support.jvm.expression.JVMSimpleCall
import me.anno.support.jvm.expression.JVMSimpleExpr
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.*
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.specialization.MethodSpecialization

abstract class MethodColoring<Color : Any> : GraphColoring<MethodSpecialization, Color>() {

    override fun getDependencies(key: MethodSpecialization) =
        getMethodDependencies(key)

    companion object {
        private val LOGGER = LogManager.getLogger(MethodColoring::class)

        fun getMethodDependencies(method: MethodSpecialization): List<MethodSpecialization> {
            // check for method calls...
            val body = method.method.getSpecializedBody(method.specialization) ?: return emptyList()
            val result = ArrayList<MethodSpecialization>()
            body.forEachExpressionRecursively { expr ->
                when (expr) { // only top-level needs to be checked
                    is YieldExpression, is ReturnExpression, is ThrowExpression,
                    is ThisExpression, is SuperExpression, is NumberExpression, is StringExpression,
                    is IfElseBranch, is WhileLoop, is DoWhileLoop, is ExpressionList, is TryCatchBlock,
                    is GetClassFromTypeExpression, is GetClassFromValueExpression,
                    is BreakExpression, is ContinueExpression, is IsInstanceOfExpr,
                    is CheckEqualsOp, is SpecialValueExpression, is LazyExpression -> {
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
                    is JVMSimpleCall -> {
                        val method1 = expr.method
                        result.add(MethodSpecialization(method1, Specializations.specialization))
                    }
                    is JVMSimpleExpr -> {}
                    is ResolvedCompareOp -> {
                        val method1 = expr.callable
                        result.add(MethodSpecialization(method1.resolved, method1.specialization))
                    }
                    is ResolvedGetFieldExpression -> {
                        val field = expr.field.resolved
                        val getter = field.getter
                        if (getter != null && (field.hasCustomGetter || field.isLateinit())) {
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