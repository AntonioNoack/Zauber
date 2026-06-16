package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.constants.SimpleNumber
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleCompare
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleCompare(
    val dst: JVMSimpleField,
    val p0: JVMSimpleField,
    val p1: JVMSimpleField?, // null means "0"
    val type: CompareType,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $p0 $type ${p1 ?: "0"}"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        val p0 = p0.toSimple(block0)
        val p1 = p1?.toSimple(block0) ?: run {
            val base = NumberExpression("0", scope, origin)
            val tmp = block0.field(p0.type, base)
            block0.add(SimpleNumber(tmp, base))
            tmp
        }
        block0.add(SimpleCompare(dst, p0, p1, type, p0.type, scope, origin))
        return flow0.withValue(dst)
    }

}