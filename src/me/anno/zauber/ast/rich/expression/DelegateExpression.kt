package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo this generates a hidden field, initializes it, and creates a setter and getter method
class DelegateExpression(val value: Expression) : Expression(value.scope, value.origin) {

    override fun toStringImpl(depth: Int): String {
        return "by ${value.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type = value.resolveType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = value.hasLambdaOrUnknownGenericsType(context)
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = false

    override fun clone(scope: Scope) = DelegateExpression(value.clone(scope))

}