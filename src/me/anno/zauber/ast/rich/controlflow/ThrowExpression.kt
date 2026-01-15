package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

// todo we maybe can pack this into an return Err(thrown), and return into return Ok(value)
class ThrowExpression(val value: Expression, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "throw ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // always Nothing
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun clone(scope: Scope) = ThrowExpression(value.clone(scope), scope, origin)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = value.isResolved()

    override fun resolve(context: ResolutionContext): Expression {
        return ThrowExpression(value.resolve(context), scope, origin)
    }

}