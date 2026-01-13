package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Generates a lambda from the base, effectively being a::b -> { a.b(allParamsNeeded) }
 * */
class GetMethodFromValueExpression(val base: Expression, val name: String, origin: Int) :
    Expression(base.scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${base.toString(depth)}::$name"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo resolve method, then resolve lambdaType from that
        TODO("Not yet implemented")
    }

    // todo or if the resolved method has some...
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return true // base.hasLambdaOrUnknownGenericsType()
    }

    override fun needsBackingField(methodScope: Scope): Boolean = base.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = false

    override fun clone(scope: Scope) = GetMethodFromValueExpression(base.clone(scope), name, origin)

}