package me.anno.support.jvm.utils

import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleSelfConstructor
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class LateSpecializationExpression(val graph: SimpleGraph, scope: Scope, origin: Int) : Expression(scope, origin) {

    private val contentAsExpressions by lazy {
        // todo this is used for tinting: exceptions, yields, ... and we must implement it somehow...
        val calls = graph.nodes.flatMap { node ->
            node.instructions.mapNotNull { instr ->
                when (instr) {
                    is SimpleCall -> JavaCallExpression(instr, scope, origin)
                    is SimpleSelfConstructor -> JavaCallExpression(instr, scope, origin)
                    is SimpleAllocateInstance -> null // todo could throw OOM...
                    else -> null
                }
            }
        }

        val thrown = graph.endFlow.thrown
        val throws = if (thrown != null) {
            // throw must be handled explicitly from what we have at the end...
            val param = TypedMysteryExpression(thrown.value.type, scope, origin)
            listOf(ThrowExpression(param, scope, origin))
        } else emptyList()
        calls + throws
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (expr in contentAsExpressions) {
            callback(expr)
        }
    }

    override fun resolveReturnType(context: ResolutionContext): Type = throw NotImplementedError()
    override fun clone(scope: Scope): Expression = throw NotImplementedError()
    override fun toStringImpl(depth: Int): String = "LateSpec($graph)"

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = throw NotImplementedError()
    override fun needsBackingField(methodScope: Scope): Boolean = throw NotImplementedError()
    override fun splitsScope(): Boolean = throw NotImplementedError()
    override fun isResolved(): Boolean = true
}