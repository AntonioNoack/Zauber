package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.constants.SimpleString
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleString(
    val dst: JVMSimpleField,
    val value: String,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = Types.String
    override fun toStringImpl(depth: Int): String = "$dst = ${style(value, GREEN)}"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val stringExpr = StringExpression(value, scope, origin)
        val dst = dst.toSimple(block0)
        block0.add(SimpleString(dst, stringExpr))
        return flow0.withValue(dst, block0)
    }
}
