package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class AnnotatedExpression(val annotation: Annotation, val value: Expression) : Expression(value.scope, value.origin) {

    override fun toStringImpl(depth: Int): String {
        return "$annotation${value.toString(depth)}"
    }

    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun resolveType(context: ResolutionContext): Type = value.resolveType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun clone(scope: Scope) = AnnotatedExpression(annotation, value.clone(scope))
}