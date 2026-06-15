package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.ASTSimplifier.nativeNumbers
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.constants.SimpleNumber
import me.anno.zauber.ast.simple.constants.SimpleSpecialValue
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.NullType

class JVMSimpleNull(
    val dst: JVMSimpleField,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = NullType
    override fun toStringImpl(depth: Int): String = "$dst = ${style("null", ORANGE)}"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        val nullInstr = when (dst.type) {
            // ensure this can be specialized to 0/0f/0.0, if type specializes to a number
            Types.Boolean -> SimpleSpecialValue(dst, SpecialValue.FALSE, scope, origin)
            in nativeNumbers -> SimpleNumber(dst, NumberExpression("0", scope, origin))
            else -> SimpleSpecialValue(dst, SpecialValue.NULL, scope, origin)
        }
        block0.add(nullInstr)
        return flow0.withValue(dst)
    }
}