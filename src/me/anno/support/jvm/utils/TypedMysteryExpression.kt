package me.anno.support.jvm.utils

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class TypedMysteryExpression(val type: Type, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun resolveReturnType(context: ResolutionContext): Type {
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope): Expression {
        TODO("Not yet implemented")
    }

    override fun toStringImpl(depth: Int): String {
        TODO("Not yet implemented")
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        TODO("Not yet implemented")
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        TODO("Not yet implemented")
    }

    override fun splitsScope(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isResolved(): Boolean {
        TODO("Not yet implemented")
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        TODO("Not yet implemented")
    }
}