package me.anno.zauber.ast.rich.expression.resolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope

abstract class ResolvedFieldExpression(
    /**
     * if not present, use 'this' or objectField
     * */
    val owner: Expression?,
    val field: ResolvedField,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    val context get() = field.context

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false

    override fun needsBackingField(methodScope: Scope): Boolean {
        return (owner != null && owner.needsBackingField(methodScope)) ||
                // todo is this correct?
                (field.resolved.name == "field" && field.resolved.codeScope == methodScope)
    }

    override fun splitsScope(): Boolean = owner?.splitsScope() ?: false
    override fun isResolved(): Boolean = owner == null || owner.isResolved()

}