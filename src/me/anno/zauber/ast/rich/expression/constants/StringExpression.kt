package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.StringType

class StringExpression(val value: String, scope: Scope, origin: Int) : Expression(scope, origin) {

    init {
        resolvedType = StringType
    }

    override fun toStringImpl(depth: Int): String = "\"$value\""

    override fun resolveType(context: ResolutionContext): Type {
        return StringType
    }

    override fun clone(scope: Scope) = StringExpression(value, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

}