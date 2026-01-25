package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType

/**
 * Return | Throw | Yield: anything stopping or exiting execution
 * */
abstract class ExitExpression(val value: Expression, val label: String?, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // type is known: Nothing / Unit
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)
    override fun isResolved(): Boolean = value.isResolved()
}