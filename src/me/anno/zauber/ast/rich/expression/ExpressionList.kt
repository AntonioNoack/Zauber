package me.anno.zauber.ast.rich.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class ExpressionList(val list: List<Expression>, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return if (list.size <= 1) "[${list.joinToString("; ") { it.toString(depth) }}]"
        else "[${list.joinToString(";") { "\n  " + it.toString(depth) }}]"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        if (list.isEmpty()) return exprHasNoType(context)
        // if any previous expression returns NothingType, return NothingType; else return the last found type
        lateinit var type: Type
        for (i in list.indices) {
            type = TypeResolution.resolveType(
                context.withAllowTypeless(context.allowTypeless || i + 1 < list.size),
                list[i]
            )
            if (type == Types.Nothing) return type
        }
        return type
    }

    override fun clone(scope: Scope) = ExpressionList(list.map { it.clone(scope) }, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        // todo if there is a 'Nothing'-returning expression, return false
        val last = list.lastOrNull() ?: return false
        return last.hasLambdaOrUnknownGenericsType(context)
    }

    override fun splitsScope(): Boolean = list.any { it.splitsScope() }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return list.any { it.needsBackingField(methodScope) }
    }

    override fun isResolved(): Boolean {
        return list.all { it.isResolved() }
    }

    override fun resolveImpl(context: ResolutionContext): Expression {
        val ctxWithoutTypeRequirement = context.withAllowTypeless(true)
        return ExpressionList(list.mapIndexed { index, expr ->
            expr.resolve(if (index == list.lastIndex) ctxWithoutTypeRequirement else context)
        }, scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (entry in list) callback(entry)
    }
}