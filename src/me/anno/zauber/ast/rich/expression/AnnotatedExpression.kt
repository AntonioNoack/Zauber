package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class AnnotatedExpression(val annotation: Annotation, val base: Expression) : Expression(base.scope, base.origin) {

    override fun toStringImpl(depth: Int): String {
        return "$annotation${base.toString(depth)}"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return base.resolveType(context)
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return base.hasLambdaOrUnknownGenericsType(context)
    }

    override fun clone(scope: Scope) = AnnotatedExpression(annotation, base.clone(scope))
}