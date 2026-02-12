package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.TokenListIndex
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.TypeExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * Some name, which must have a dot before it -> member/extension field/method
 * */
class MemberNameExpression(
    val name: String,
    val nameAsImport: List<Import>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    companion object {
        fun ASTBuilderBase.nameExpression(name: String, origin: Int, scope: Scope): Expression {
            // check whether something is in front (some sort of period/accessor)
            val isChild = when {
                tokens.equals(origin - 1, ".") ||
                        tokens.equals(origin - 1, "?.") -> true
                else -> false
            }
            return if (isChild) {
                MemberNameExpression(name, nameAsImport(name), scope, origin)
            } else {
                val type = TypeResolution.findType(scope, null, name)
                if (type != null) return TypeExpression(type, scope, origin)

                // try and simply find parameters, if possible
                var scopeI = scope
                while (true) {
                    val fieldMatch = scopeI.fields.firstOrNull { it.name == name }
                    if (fieldMatch != null) return FieldExpression(fieldMatch, scope, origin)

                    if (scopeI.scopeType?.isInsideExpression() != true) {
                        // the surrounding scope is fine, too,
                        //  but then we should stop,
                        //  because inheritance may mislead us
                        scopeI = scopeI.parentIfSameFile ?: break
                        val fieldMatch = scopeI.fields.firstOrNull { it.name == name }
                        if (fieldMatch != null) return FieldExpression(fieldMatch, scope, origin)

                        break
                    }
                    scopeI = scopeI.parentIfSameFile ?: break
                }

                // try to find the field:
                //  check super scopes until a class appears,
                //  then check hierarchy scopes
                //  -> inner classes (anonymous inline classes) have access to multiple scopes
                //  -> for the whole hierarchy, try to find fields and parent classes
                // when this is executed, the field might not yet be known
                //  -> and we must respect the hierarchy -> we can only execute this later on
                UnresolvedFieldExpression(name, nameAsImport(name), scope, origin)
            }
        }
    }

    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = MemberNameExpression(name, nameAsImport, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false // 'field' will not have a dot
    override fun isResolved(): Boolean = false
    override fun splitsScope(): Boolean = false

    override fun resolveReturnType(context: ResolutionContext): Type {
        val field = FieldResolver.resolveField(context, scope, name, nameAsImport, null, origin)
        if (field != null) return field.getValueType()

        throw IllegalStateException(
            "Missing field/type '${name}' in ${context.selfType}, $scope, " +
                    TokenListIndex.resolveOrigin(origin)
        )
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {}
}