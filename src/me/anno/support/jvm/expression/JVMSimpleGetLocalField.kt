package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleGetLocalField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleGetLocalField(
    val dst: JVMSimpleField, val field: JVMLocalField,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = dst.type.specialize(context)
    override fun toStringImpl(depth: Int): String = "$dst = $field"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val dst = dst.toSimple(block0)
        val localField = block0.graph.getOrPutLocalField(field)

        println("JVMSimpleGetLocalField this.dst: ${this.dst}, dst: $dst, field: $localField")

        block0.add(SimpleGetLocalField(dst, localField, scope, origin))
        return flow0.withValue(dst)
    }
}