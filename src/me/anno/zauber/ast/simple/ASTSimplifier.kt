package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.expression.CallExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleConstructor
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope

object ASTSimplifier {

    fun simplify(context: ResolutionContext, expr: Expression): SimpleGraph {
        val graph = SimpleGraph()
        simplifyCode(context, expr, graph.entry, graph)
        return graph
    }

    private fun simplifyCode(
        context: ResolutionContext,
        expr: Expression,
        addToBlock: SimpleBlock,
        graph: SimpleGraph
    ) {
        when (expr) {
            is ExpressionList -> {
                for (expr in expr.list) {
                    simplifyCode(context, expr, addToBlock, graph)
                }
            }
            is ThrowExpression -> {
                // todo we need to check all handlers...
                //   and finally, we need to exit
            }
            is ReturnExpression -> {
                // this is the standard way to exit,
                //  but todo we also want yields for async functions and sequences
                val field = simplifyValue(context, expr.value, addToBlock, graph)
                addToBlock.add(SimpleReturn(field, expr.scope, expr.origin))
            }
            else -> TODO("Simplify code ${expr.javaClass.simpleName}")
        }
    }

    private fun simplifyValue(
        context: ResolutionContext,
        expr: Expression,
        addToBlock: SimpleBlock,
        graph: SimpleGraph
    ): SimpleField {
        when (expr) {
            is CallExpression -> {
                // first the callee,
                //  then all arguments,
                // todo join varargs...
                when (val method = expr.resolveMethod(context).resolved) {
                    is Method -> {
                        val base = simplifyValue(context, expr.base, addToBlock, graph)
                        val params =
                            reorderParameters(expr.valueParameters, method.valueParameters, expr.scope, expr.origin)
                                .map { parameter ->
                                    simplifyValue(context, parameter, addToBlock, graph)
                                }
                        // then execute it
                        val dst = addToBlock.field(expr)
                        addToBlock.add(SimpleCall(method, base, params, expr.scope, expr.origin))
                        return dst
                    }
                    is Constructor -> {
                        // todo what even is "base???"
                        // val base = simplifyValue(context, expr.base, addToBlock, graph)
                        val params =
                            reorderParameters(
                                expr.valueParameters,
                                method.valueParameters,
                                expr.scope, expr.origin
                            ).map { parameter ->
                                simplifyValue(context, parameter, addToBlock, graph)
                            }
                        // then execute it
                        val dst = addToBlock.field(expr)
                        addToBlock.add(SimpleConstructor(method, params, expr.scope, expr.origin))
                        return dst
                    }
                    else -> TODO("Simplify value ${expr.javaClass.simpleName}")
                }
            }
            else -> TODO("Simplify value ${expr.javaClass.simpleName}")
        }
    }

    fun reorderParameters(
        src: List<NamedParameter>, dst: List<Parameter>,
        scope: Scope, origin: Int
    ): List<Expression> {
        return resolveNamedParameters(dst, src, scope, origin)
            ?: throw IllegalStateException("Failed to fill in call parameters")
    }

}