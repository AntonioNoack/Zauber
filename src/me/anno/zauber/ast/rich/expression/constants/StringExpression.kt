package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.constants.SimpleString
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class StringExpression(val value: String, scope: Scope, origin: Long) : Expression(scope, origin) {

    init {
        resolvedType = Types.String
    }

    override fun toStringImpl(depth: Int): String = "\"$value\""

    override fun resolveValueType(context: ResolutionContext): Type = Types.String
    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun clone(scope: Scope) = StringExpression(value, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}

    override fun replaceLambdaFieldsWithClassFields(oldFields: List<Field>, newFields: List<Field>) = this

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        val dst = block0.field(Types.String, this)
        block0.add(SimpleString(dst, this))
        return flow0.withValue(dst, block0)
    }

}