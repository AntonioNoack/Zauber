package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier.simplifyJump
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class ContinueExpression(val label: Scope, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "continue@$label"
    }

    override fun resolveValueType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun clone(scope: Scope): Expression = ContinueExpression(label, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return type
    override fun needsBackingField(methodScope: Scope): Boolean = false

    // execution ends here anyway
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    override fun forEachExpression(callback: (Expression) -> Unit) {}

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        return simplifyJump(this, flow0, block0.graph.continueLabels[label])
    }
}