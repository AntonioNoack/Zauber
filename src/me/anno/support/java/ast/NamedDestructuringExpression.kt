package me.anno.support.java.ast

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaVariable
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class NamedDestructuringExpression(val type: Type, val names: List<LambdaVariable?>, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun resolveType(context: ResolutionContext): Type = BooleanType
    override fun clone(scope: Scope): Expression = this

    override fun toStringImpl(depth: Int): String {
        return "(is $type(${names.joinToString()}))"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // Boolean is clear
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (name in names) {
            name ?: continue
            callback(name.field.initialValue!!)
        }
    }
}