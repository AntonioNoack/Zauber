package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier.unitInstance
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleConstructorCall
import me.anno.zauber.ast.simple.expression.SimpleInstanceOf.Companion.createSimpleInstanceOf
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class JVMSimpleCheckCast(
    val value: JVMSimpleField,
    val type: ClassType,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = Types.Boolean
    override fun toStringImpl(depth: Int): String = "Check $value is $type"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val graph = block0.graph
        val ifTrue = graph.addBlock()
        val ifFalse = graph.addBlock()

        val condition = block0.field(Types.Boolean)
        val value = value.toSimple(block0)
        block0.add(createSimpleInstanceOf(condition, value, type, scope, origin))

        val unit = unitInstance(graph, this)

        val thrown = ifFalse.field(Types.ClassCastException)
        val spec = Specialization(Types.ClassCastException)
        val params = emptyList<SimpleField>()
        ifFalse.add(
            SimpleAllocateInstance(
                thrown, Types.ClassCastException,
                params, spec, scope, origin
            )
        )

        val constr = Types.ClassCastException.clazz[ScopeInitType.AFTER_OVERRIDES]
            .constructors0.firstOrNull { it.valueParameters.isEmpty() }
            ?: error("Missing ClassCastException-constructor without value parameters")
        val constrSpec = spec.withScope(constr.memberScope)
        ifFalse.add(SimpleConstructorCall(unit, thrown, constrSpec, params, scope, origin))

        block0.ifBranch = ifTrue
        block0.elseBranch = ifFalse
        block0.branchCondition = condition

        return flow0
            .withValue(unit, ifTrue)
            .joinThrownNoValue(thrown, ifFalse)
    }
}