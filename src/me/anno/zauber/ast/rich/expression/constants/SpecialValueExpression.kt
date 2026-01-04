package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.resolveThisType
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.impl.NullType

class SpecialValueExpression(val value: SpecialValue, scope: Scope, origin: Int) : Expression(scope, origin) {
    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = value.name.lowercase()
    override fun resolveType(context: ResolutionContext): Type {
        return when (value) {
            SpecialValue.NULL -> NullType
            SpecialValue.TRUE, SpecialValue.FALSE -> BooleanType
            SpecialValue.THIS -> {
                // todo 'this' might have a label, and then means the parent with that name
                resolveThisType(typeToScope(context.selfType) ?: context.codeScope).typeWithoutArgs
            }
            else -> TODO("Resolve type for ConstantExpression in ${context.codeScope},${value}")
        }
    }

    override fun clone(scope: Scope) = SpecialValueExpression(value, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false // should not have any
}