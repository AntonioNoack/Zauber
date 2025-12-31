package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.findType
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class LazyFieldOrTypeExpression(
    val name: String,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = LazyFieldOrTypeExpression(name, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        val context = context.withCodeScope(scope)
        val field = resolveField(context, name, null)
        if (field != null) return field.getValueType(context)

        val type = findType(context.codeScope, context.selfType, name)
        if (type != null) return type

        throw IllegalStateException(
            "Missing field/type '${name}' in ${context.selfType}, ${context.codeScope}, " +
                    resolveOrigin(origin)
        )
    }
}