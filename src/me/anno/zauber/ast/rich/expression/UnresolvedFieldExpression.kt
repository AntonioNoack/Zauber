package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class UnresolvedFieldExpression(
    val name: String,
    val nameAsImport: Scope?,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = UnresolvedFieldExpression(name, nameAsImport, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false

    // todo what if 'field' is shadowed?
    override fun needsBackingField(methodScope: Scope): Boolean = name == "field"

    fun resolveField(context: ResolutionContext): ResolvedField? {
        val context = context.withCodeScope(scope)
        return resolveField(context, name, null, origin)
    }

    override fun resolveType(context: ResolutionContext): Type {
        val context = context.withCodeScope(scope)
        val field = resolveField(context, name, null, origin)
        if (field != null) return field.getValueType(context)

        val nameAsImport = nameAsImport
        if (nameAsImport != null) {
            return ImportedMember(nameAsImport, scope, origin)
                .resolveType(context)
        }

        throw IllegalStateException(
            "Missing field '${name}' in ${context.selfType}, ${context.codeScope}, " +
                    resolveOrigin(origin)
        )
    }
}