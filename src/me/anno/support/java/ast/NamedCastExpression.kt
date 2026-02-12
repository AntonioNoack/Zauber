package me.anno.support.java.ast

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.NothingType

/**
 * if (value instanceof T newName) {
 *  ... newName is now a valid T ...
 * }
 * */
class NamedCastExpression(val instanceTest: IsInstanceOfExpr, val newName: String) :
    Expression(instanceTest.scope, instanceTest.origin) {

    override fun resolveReturnType(context: ResolutionContext): Type = BooleanType
    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    override fun clone(scope: Scope): Expression = NamedCastExpression(instanceTest.clone(scope), newName)

    override fun toStringImpl(depth: Int): String = "$newName[${instanceTest.toString(depth)}]"

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // Boolean is clear

    override fun needsBackingField(methodScope: Scope): Boolean {
        return instanceTest.needsBackingField(methodScope)
    }

    override fun splitsScope(): Boolean = instanceTest.splitsScope()
    override fun isResolved(): Boolean = instanceTest.isResolved()

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(instanceTest)
    }
}