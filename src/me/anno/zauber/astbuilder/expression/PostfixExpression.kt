package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class PostfixExpression(val base: Expression, val type: PostfixType, origin: Int) : Expression(base.scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
    }

    override fun toString(depth: Int): String {
        return "${base.toString(depth - 1)}${type.symbol}"
    }

    override fun clone(scope: Scope) = PostfixExpression(base.clone(scope), type, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return base.hasLambdaOrUnknownGenericsType()
    }

    override fun resolveType(context: ResolutionContext): Type {
        return TypeResolution.resolveType(context, base)
    }
}