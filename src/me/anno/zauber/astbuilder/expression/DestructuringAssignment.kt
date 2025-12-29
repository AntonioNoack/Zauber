package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

// todo generate a temporary variable, and then just assign all of them in an expression list :)
class DestructuringAssignment(
    val names: List<String>, val initialValue: Expression,
    val isVar: Boolean, val isLateinit: Boolean
) : Expression(initialValue.scope, initialValue.origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {
        callback(initialValue)
    }

    override fun toString(depth: Int): String {
        return (if (isVar) if (isLateinit) "lateinit var" else "var " else "val ") +
                "(${names.joinToString()}) = ${initialValue.toString(depth - 1)}"
    }

    override fun resolveType(context: ResolutionContext): Type = exprHasNoType(context)

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun clone(scope: Scope) = DestructuringAssignment(names, initialValue.clone(scope), isVar, isLateinit)

}