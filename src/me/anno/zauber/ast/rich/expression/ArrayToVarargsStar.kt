package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class ArrayToVarargsStar(val base: Expression) : Expression(base.scope, base.origin) {
    override fun resolveType(context: ResolutionContext): Type = base.resolveType(context)
    override fun clone(scope: Scope) = ArrayToVarargsStar(base.clone(scope))
    override fun toStringImpl(depth: Int): String = "*${base.toStringImpl(depth)}"
    override fun hasLambdaOrUnknownGenericsType(): Boolean = base.hasLambdaOrUnknownGenericsType()

}