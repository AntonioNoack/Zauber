package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class BreakExpression(val label: String?, scope: Scope, origin: Int) : Expression(scope, origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String {
        return if (label != null) "break@$label" else "break"
    }

    override fun resolveType(context: ResolutionContext): Type {
        return exprHasNoType(context)
    }

    override fun clone(scope: Scope): Expression = BreakExpression(label, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // has no return type
}