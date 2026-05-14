package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Member
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization

abstract class ResolvedMember<V : Member>(
    val selfTypeParameters: ParameterList,
    val callTypeParameters: ParameterList,
    val resolved: V,
    val context: ResolutionContext,
    val codeScope: Scope,
    val matchScore: MatchScore,
    origin: Int
) {

    init {
        check(!selfTypeParameters.containsNull()) {
            "All self-type-params must be resolved,\n" +
                    "  within $this,\n" +
                    "  at ${resolveOrigin(origin)}"
        }
        check(!callTypeParameters.containsNull()) {
            "All call-type-params must be resolved,\n" +
                    "  within $this,\n" +
                    "  at ${resolveOrigin(origin)}"
        }
    }

    val selfType get() = context.selfType
    val specialization = Specialization(selfTypeParameters + callTypeParameters)

    abstract fun getTypeFromCall(): Type
    abstract fun getScopeOfResolved(): Scope

    fun getBaseIfMissing(scope: Scope, origin: Int): Expression {
        val type = selfType?.resolve()
        if (type == null) {
            val resolvedScope = getScopeOfResolved()
            var baseScope = resolvedScope
            while (true) {
                val scopeType = baseScope.scopeType
                if (scopeType != null && (scopeType.isClassLike() || scopeType == ScopeType.PACKAGE)) {
                    return ThisExpression(baseScope, codeScope, origin)
                }
                baseScope = baseScope.parent
                    ?: throw IllegalStateException("Resolved must be in class or package, but found nothing $resolvedScope")
            }
        }

        if (type is ClassType) {
            check(type.clazz.isClassLike()) // just in case
            return ThisExpression(type.clazz, scope, origin)
        }

        val specialization = specialization
        if (type in specialization) {
            throw IllegalStateException("Type $type is in specialization, but not resolved?? $specialization")
        }

        TODO("GetBaseIfMissing on $type (${type.javaClass.simpleName}), spec: $specialization")
    }

    companion object {
        fun resolveGenerics(
            selfType: Type?, type: Type,
            genericNames: List<Parameter>,
            genericValues: ParameterList?
        ): Type {
            if (genericValues == null) return type
            return type.resolveGenerics(selfType, genericNames, genericValues)
        }
    }
}