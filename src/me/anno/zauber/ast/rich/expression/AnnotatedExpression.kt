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

    override fun resolveReturnType(context: ResolutionContext): Type = value.resolveReturnType(context)
    override fun resolveThrownType(context: ResolutionContext): Type = value.resolveThrownType(context)
    override fun resolveYieldedType(context: ResolutionContext): Type = value.resolveYieldedType(context)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean =
        value.hasLambdaOrUnknownGenericsType(context)

    override fun splitsScope(): Boolean = value.splitsScope()

    override fun clone(scope: Scope) = AnnotatedExpression(annotation, value.clone(scope))
    override fun isResolved(): Boolean = annotation.path.isResolved() && value.isResolved()
    override fun resolveImpl(context: ResolutionContext) =
        AnnotatedExpression(annotation, value.resolve(context))
    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(value)
    }
}