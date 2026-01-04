package me.anno.zauber.astbuilder.expression

import me.anno.zauber.astbuilder.ASTBuilder
import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.findType
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class MemberNameExpression(
    val name: String,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    companion object {
        fun nameExpression(name: String, origin: Int, astBuilder: ASTBuilder, scope: Scope): Expression {
            // todo check whether something is in front (some sort of period/accessor)
            val isChild = when {
                astBuilder.tokens.equals(origin - 1, ".") ||
                        astBuilder.tokens.equals(origin - 1, "?.") -> true
                else -> false
            }
            return if (isChild) {
                MemberNameExpression(name, scope, origin)
            } else {
                val nameAsImport = astBuilder.imports.firstOrNull { it.name == name }?.path
                if (nameAsImport != null) {
                    ImportedExpression(nameAsImport, scope, origin)
                } else {

                    val type = findType(scope, null, name)
                    if (type != null) return NamedTypeExpression(type, scope, origin)

                    // try to find the field:
                    //  check super scopes until a class appears,
                    //  then check hierarchy scopes
                    //  -> inner classes (anonymous inline classes) have access to multiple scopes
                    //  -> for the whole hierarchy, try to find fields and parent classes
                    // when this is executed, the field might not yet be known
                    //  -> and we must respect the hierarchy -> we can only execute this later on
                    UnresolvedFieldExpression(name, scope, origin)
                }
            }
        }
    }

    override fun forEachExpr(callback: (Expression) -> Unit) {}
    override fun toStringImpl(depth: Int): String = name
    override fun clone(scope: Scope) = MemberNameExpression(name, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(): Boolean = false

    override fun resolveType(context: ResolutionContext): Type {
        val field = resolveField(context, name, null)
        if (field != null) return field.getValueType(context)

        throw IllegalStateException(
            "Missing field/type '${name}' in ${context.selfType}, ${context.codeScope}, " +
                    resolveOrigin(origin)
        )
    }
}