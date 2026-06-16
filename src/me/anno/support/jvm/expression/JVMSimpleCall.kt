package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.ASTSimplifier.simplifyCall
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type

class JVMSimpleCall(
    val dst: JVMSimpleField,
    val method: MethodLike,
    val self: JVMSimpleField,
    val specialization: Specialization,
    val valueParameters: List<JVMSimpleField>,
    val needsResolution: Boolean,

    scope: Scope, origin: Long,
) : JVMSimpleExpr(scope, origin) {

    init {
        check(specialization.isMethodLike())
    }

    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $self.$method($valueParameters)"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val callable = resolveCallable(context)
        val selfExpr = self.toSimple(block0)
        val valueParameters = valueParameters.map { param ->
            param.toSimple(block0)
        }

        val dst = dst.toSimple(block0)
        return simplifyCall(
            dst, block0, flow0, selfExpr, needsResolution, null,
            valueParameters, callable, false,
            scope, origin
        )
    }

    private fun resolveCallable(context: ResolutionContext): ResolvedMember<*> {
        val spec = context.specialization.withScopeUnknownIfMissing(method.memberScope)
        val contextI = context.withSpec(spec)
        return when (method) {
            is Constructor -> ResolvedConstructor(method, contextI, scope, MatchScore.zero)
            is Method -> ResolvedMethod(method, contextI, scope, MatchScore.zero)
            else -> throw NotImplementedError("Resolve $method in $context")
        }
    }
}