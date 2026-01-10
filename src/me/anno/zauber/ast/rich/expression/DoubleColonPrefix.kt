package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * ::callMeNow -> type is some lambda
 * */
class DoubleColonPrefix(val left: Scope, val methodName: String, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "($left)::$methodName"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo we need to resolve the method...
        TODO("Not yet implemented")
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = true

    override fun clone(scope: Scope) = DoubleColonPrefix(left, methodName, scope, origin)

}