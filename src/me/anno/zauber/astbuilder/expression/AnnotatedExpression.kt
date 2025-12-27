package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.Annotation
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class AnnotatedExpression(val annotation: Annotation, val base: Expression) : Expression(base.scope, base.origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(base)
    }

    override fun toString(): String {
        return "$annotation$base"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return base.resolveType(context)
    }

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        return base.hasLambdaOrUnknownGenericsType()
    }

    override fun clone(scope: Scope) = AnnotatedExpression(annotation, base.clone(scope))
}