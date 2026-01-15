package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class UnresolvedFieldExpression(
    val name: String,
    val nameAsImport: List<Import>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = UnresolvedFieldExpression(name, nameAsImport, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false

    // todo what if 'field' is shadowed?
    override fun needsBackingField(methodScope: Scope): Boolean = name == "field"

    fun resolveField(context: ResolutionContext): ResolvedField {
        return FieldResolver.resolveField(context, scope, name, nameAsImport, null, origin)
            ?: throw IllegalStateException("Failed to resolve field $name in $scope")
    }

    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        return resolveField(context).getValueType()
    }
}