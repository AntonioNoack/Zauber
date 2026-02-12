package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class GetMethodFromTypeExpression(val base: Scope, val name: String, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "$base::$name"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        // todo resolve method, then convert signature into lambda
        TODO("Not yet implemented")
    }

    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    // todo if the base has some...
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = base.typeParameters.isNotEmpty()
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false

    override fun clone(scope: Scope) = GetMethodFromTypeExpression(base, name, scope, origin)
    override fun isResolved(): Boolean = false
    override fun forEachExpression(callback: (Expression) -> Unit) {}

}