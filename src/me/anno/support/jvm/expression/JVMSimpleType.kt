package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleGetTypeInstance
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class JVMSimpleType(
    val dst: JVMSimpleField,
    val value: ClassType,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = Types.ClassType.withTypeParameter(value)
    override fun toStringImpl(depth: Int): String = "$dst = Type[$value]"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = dst.toSimple(block0)
        block0.add(SimpleGetTypeInstance(dst, value, scope, origin))
        return flow0.withValue(dst)
    }
}
