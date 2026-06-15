package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleCheckEquals
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class JVMSimpleCheckEquals(
    val dst: JVMSimpleField,
    val p0: JVMSimpleField, val p1: JVMSimpleField,
    val negated: Boolean,
    val method: ResolvedMethod,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $p0 ${if (negated) "!=" else "=="} $p1"

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

        // todo SimpleCheckEquals does too much stuff
        val methods = (p0.type as ClassType).clazz.methods0
        val method =
            methods.firstOrNull {
                it.name == "equals" && it.valueParameters.size == 1 &&
                        it.valueParameters.first() == p1
            } ?: methods.firstOrNull {
                it.name == "equals" && it.valueParameters.size == 1 &&
                        it.valueParameters.first() == Types.NullableAny
            }
            ?: error("Missing $p0.equals($p1/Any?)")

        val callable = ResolvedMethod(
            method, ResolutionContext.minimal.withSpec(
                Specialization(p0.type).withScope(method.memberScope)
            ), scope, MatchScore.zero
        )

        val instr = SimpleCheckEquals(dst, p0, p1, negated, callable, scope, origin)
        block0.add(instr)
        return flow0.withValue(dst)

    }
}