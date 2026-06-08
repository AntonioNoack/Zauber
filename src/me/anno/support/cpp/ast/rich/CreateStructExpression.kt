package me.anno.support.cpp.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class CreateStructExpression(val fields: List<Expression>, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type {
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope) = CreateStructExpression(fields, scope, origin)

    override fun toStringImpl(depth: Int): String {
        return "{ ${fields.joinToString(", ") { field -> field.toString(depth) }} }"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return fields.any { it.hasLambdaOrUnknownGenericsType(context) }
    }

    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (value in fields) callback(value)
    }
}