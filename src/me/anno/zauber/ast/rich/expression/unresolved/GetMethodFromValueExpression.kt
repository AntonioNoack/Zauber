package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Generates a lambda from the base, effectively being a::b -> { a.b(allParamsNeeded) }
 * */
class GetMethodFromValueExpression(val self: Expression, val name: String, origin: Int) :
    Expression(self.scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${self.toString(depth)}::$name"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo resolve method, then resolve lambdaType from that
        TODO("Not yet implemented")
    }

    // todo or if the resolved method has some...
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return true // base.hasLambdaOrUnknownGenericsType()
    }

    override fun needsBackingField(methodScope: Scope): Boolean = self.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun clone(scope: Scope) = GetMethodFromValueExpression(self.clone(scope), name, origin)
    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(self)
    }

}