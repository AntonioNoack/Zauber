package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.NullType

class SpecialValueExpression(val type: SpecialValue, scope: Scope, origin: Int) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = type.name.lowercase()
    override fun resolveReturnType(context: ResolutionContext): Type {
        return when (type) {
            SpecialValue.NULL -> NullType
            SpecialValue.TRUE, SpecialValue.FALSE -> BooleanType
            else -> throw NotImplementedError("Resolve type for ConstantExpression in ${scope},${type}")
        }
    }

    // in theory for booleans that of their constructor, but that really should not crash or yield...
    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    override fun clone(scope: Scope) = SpecialValueExpression(type, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false // should not have any
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}