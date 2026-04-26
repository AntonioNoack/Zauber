package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ResolvedGetFieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NonObjectClassType

class UnresolvedFieldExpression(
    val name: String,
    val nameAsImport: List<Import>,
    scope: Scope, origin: Int
) : Expression(scope, origin), FieldResolvable {

    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = UnresolvedFieldExpression(name, nameAsImport, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false

    // todo what if 'field' is shadowed?
    override fun needsBackingField(methodScope: Scope): Boolean = name == "field"

    override fun resolveField(context: ResolutionContext): ResolvedField? {
        return FieldResolver.resolveField(context, scope, name, nameAsImport, null, origin)
    }

    fun onMissingField(): Nothing {
        throw IllegalStateException("Failed to resolve field $name in $scope")
    }

    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun resolveReturnType(context: ResolutionContext): Type {
        val field = resolveField(context)
        return field?.getValueType() ?: run {
            val type0 = scope.resolveType(name, nameAsImport)
                .specialize(context)
            check(type0 is ClassType) { "Expected $type0 from $this to be ClassType" }
            NonObjectClassType(type0)
        }
    }

    // todo this would be a getter by default... resolve its type...
    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun resolveImpl(context: ResolutionContext): ResolvedGetFieldExpression {
        val field = resolveField(context) ?: onMissingField()
        val owner = field.resolveOwnerWithoutLeftSide(scope, origin)
        return ResolvedGetFieldExpression(owner, field, scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {}
}