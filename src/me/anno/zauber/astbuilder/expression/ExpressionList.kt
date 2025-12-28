package me.anno.zauber.astbuilder.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

class ExpressionList(val list: List<Expression>, scope: Scope, origin: Int) : Expression(scope, origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {
        for (i in list.indices) {
            callback(list[i])
        }
    }

    override fun toString(): String {
        return "[${list.joinToString("; ")}]"
    }

    override fun resolveType(context: ResolutionContext): Type {
        if (list.isEmpty()) return exprHasNoType(context)
        // if any previous expression returns NothingType, return NothingType; else return the last found type
        lateinit var type: Type
        for (i in list.indices) {
            type = TypeResolution.resolveType(context.withAllowTypeless(context.allowTypeless || i+1<list.size), list[i])
            if (type == NothingType) return NothingType
        }
        return type
    }

    override fun clone(scope: Scope) = ExpressionList(list.map { it.clone(scope) }, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean {
        val last = list.lastOrNull() ?: return false
        return last.hasLambdaOrUnknownGenericsType()
    }

}