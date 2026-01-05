package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class UnresolvedFieldExpression(
    val name: String,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = UnresolvedFieldExpression(name, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    fun resolveField(context: ResolutionContext): ResolvedField? {
        val context = context.withCodeScope(scope)
        return resolveField(context, name, null)
    }

    override fun resolveType(context: ResolutionContext): Type {
        val context = context.withCodeScope(scope)
        val field = resolveField(context, name, null)
        if (field != null) return field.getValueType(context)

        throw IllegalStateException(
            "Missing field '${name}' in ${context.selfType}, ${context.codeScope}, " +
                    resolveOrigin(origin)
        )
    }
}