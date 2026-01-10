package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ContinueExpression(val label: String?, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return if (label != null) "continue@$label" else "continue"
    }

    override fun resolveType(context: ResolutionContext): Type = NothingType
    override fun clone(scope: Scope): Expression = ContinueExpression(label, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return type
}