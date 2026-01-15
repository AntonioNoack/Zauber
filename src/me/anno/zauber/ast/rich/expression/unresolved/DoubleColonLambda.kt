package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * ::callMeNow -> type is some lambda
 * */
class DoubleColonLambda(val left: Scope, val methodName: String, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "($left)::$methodName"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo we need to resolve the method...
        TODO("Not yet implemented")
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = true
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false // some lambda -> no
    override fun isResolved(): Boolean = false

    override fun clone(scope: Scope) = DoubleColonLambda(left, methodName, scope, origin)

}