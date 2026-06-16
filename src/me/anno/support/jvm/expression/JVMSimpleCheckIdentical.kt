package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleCheckIdentical
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleCheckIdentical(
    val dst: JVMSimpleField,
    val p0: JVMSimpleField, val p1: JVMSimpleField,
    val negated: Boolean,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $p0 ${if (negated) "!==" else "==="} $p1"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val dst = dst.toSimple(block0)
        val p0 = p0.toSimple(block0)
        val p1 = p1.toSimple(block0)

        val instr = SimpleCheckIdentical(dst, p0, p1, negated, scope, origin)
        block0.add(instr)
        return flow0.withValue(dst)
    }
}