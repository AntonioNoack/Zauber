package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class GetMethodFromTypeExpression(val base: Scope, val name: String, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "$base::$name"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo resolve method, then convert signature into lambda
        TODO("Not yet implemented")
    }

    // todo if the base has some...
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = base.typeParameters.isNotEmpty()

    override fun clone(scope: Scope) = GetMethodFromTypeExpression(base, name, scope, origin)

}