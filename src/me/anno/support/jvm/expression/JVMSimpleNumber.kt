package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.constants.SimpleNumber
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleNumber(
    val dst: SimpleFieldExpr,
    val value: NumberExpression,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $value"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        block0.add(SimpleNumber(dst, value))
        return flow0.withValue(dst, block0)
    }
}