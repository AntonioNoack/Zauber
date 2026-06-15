package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class JVMSimpleAllocateInstance(
    val dst: SimpleFieldExpr,
    val allocatedType: ClassType,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = allocatedType
    override fun toStringImpl(depth: Int): String = "$dst = ${style("new", ORANGE)} $allocatedType"

    lateinit var valueParameters: List<SimpleFieldExpr>

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        val valueParameters = valueParameters
            .map { param -> param.toSimple(block0) }
        block0.add(
            SimpleAllocateInstance(
                dst, allocatedType, valueParameters,
                Specialization(allocatedType),
                scope, origin
            )
        )
        return flow0.withValue(dst)
    }
}