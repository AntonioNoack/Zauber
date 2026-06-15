package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleSetLocalField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleSetLocalField(
    val field: JVMLocalField, val value: JVMSimpleField,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = Types.Unit
    override fun toStringImpl(depth: Int): String = "${style(field.name, YELLOW)} = $value"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val localField = block0.graph.getOrPutLocalField(field)
        block0.add(SimpleSetLocalField(localField, value.toSimple(block0).use(), scope, origin))
        return flow0.withValue(ASTSimplifier.unitInstance(block0.graph, this))
    }
}