package me.anno.zauber.ast.rich.expression

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType

class IsInstanceOfExpr(val value: Expression, val type: Type, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    val symbol: String get() = "is"

    override fun toStringImpl(depth: Int): String {
        return "(${value.toString(depth)})is($type)"
    }

    override fun resolveType(context: ResolutionContext): Type = BooleanType
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // always boolean
    override fun needsBackingField(methodScope: Scope): Boolean = value.needsBackingField(methodScope)

    override fun clone(scope: Scope): Expression {
        return IsInstanceOfExpr(value.clone(scope), type, scope, origin)
    }

    // can change type information...
    override fun splitsScope(): Boolean = true
    override fun isResolved(): Boolean = value.isResolved() && type.isResolved()
    override fun resolveImpl(context: ResolutionContext): Expression {
        return IsInstanceOfExpr(value.resolve(context), type.resolve(), scope, origin)
    }
}