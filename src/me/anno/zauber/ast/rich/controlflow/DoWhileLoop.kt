package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * cannot easily be converted to a while-loop, because continue needs to run the evaluation!
 * */
class DoWhileLoop(val body: Expression, val condition: Expression, val label: String?) :
    Expression(condition.scope, condition.origin) {

    override fun toStringImpl(depth: Int): String {
        return "${if (label != null) "$label@" else ""} do { ${body.toString(depth)} } while (${condition.toString(depth)})"
    }

    override fun resolveType(context: ResolutionContext): Type =exprHasNoType(context)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // this has no return value

    override fun clone(scope: Scope) =
        DoWhileLoop(body = body.clone(body.scope), condition = condition.clone(scope), label)

}