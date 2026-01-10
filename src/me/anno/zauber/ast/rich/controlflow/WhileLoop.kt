package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class WhileLoop(val condition: Expression, val body: Expression, val label: String?) :
    Expression(condition.scope, condition.origin) {

    override fun toStringImpl(depth: Int): String {
        return "${if (label != null) "$label@" else ""} while(${condition.toString(depth)}) { ${body.toString(depth)} }"
    }

    override fun resolveType(context: ResolutionContext): Type =exprHasNoType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return value

    override fun clone(scope: Scope) = WhileLoop(condition.clone(scope), body.clone(body.scope), label)

}