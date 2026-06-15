package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleGetLocalField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleGetLocalField(
    val dst: SimpleFieldExpr, val graph: JVMGraph, val field: Field,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = dst.type.specialize(context)
    override fun toStringImpl(depth: Int): String = "$dst = ${style(field.name, YELLOW)}"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        val localField = if (graph.thisField == field) {
            block0.graph.thisField!!
        } else {
            block0.graph.getOrPutLocalField(field, context)
        }

        block0.add(SimpleGetLocalField(dst, localField, scope, origin))
        return flow0.withValue(dst)
    }
}