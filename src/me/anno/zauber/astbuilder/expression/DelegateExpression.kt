package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo this generates a hidden field, initializes it, and creates a setter and getter method
class DelegateExpression(val delegate: Expression) : Expression(delegate.scope, delegate.origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(delegate)
    }

    override fun toStringImpl(depth: Int): String {
        return "by ${delegate.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type {
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope) = DelegateExpression(delegate.clone(scope))

}