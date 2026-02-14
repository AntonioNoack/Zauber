package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TypeOfField
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenericsOrNull
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.specialization.Specialization

abstract class ResolvedMember<V>(
    val selfTypeParameters: ParameterList,
    val callTypeParameters: ParameterList,
    val resolved: V,
    val context: ResolutionContext,
    val codeScope: Scope,
    val matchScore: MatchScore
) {

    init {
        check(!selfTypeParameters.containsNull()) { "All owner-types within $this must be resolved" }
        check(!callTypeParameters.containsNull()) { "All call-types within $this must be resolved" }
    }

    val selfType get() = context.selfType
    val specialization = Specialization(selfTypeParameters + callTypeParameters)

    abstract fun getTypeFromCall(): Type
    abstract fun getScopeOfResolved(): Scope

    fun getBaseIfMissing(scope: Scope, origin: Int): Expression {
        val type = selfType?.resolve()
        if (type == null) {
            var rs = getScopeOfResolved()
            while (true) {
                val scopeType = rs.scopeType
                if (scopeType != null && (scopeType.isClassType() || scopeType == ScopeType.PACKAGE)) {
                    return ThisExpression(rs, codeScope, origin)
                }
                rs = rs.parent
                    ?: throw IllegalStateException("Resolved must be in class or package, but found nothing ${getScopeOfResolved()}")
            }
        }

        if (type is ClassType) {
            check(type.clazz.isClassType()) // just in case
            return ThisExpression(type.clazz, scope, origin)
        }

        val specialization = specialization
        if (type in specialization) {
            throw IllegalStateException("Type $type is in specialization, but not resolved?? $specialization")
        }

        TODO("GetBaseIfMissing on $type (${type.javaClass.simpleName}), spec: $specialization")
    }

    companion object {

        private val LOGGER = LogManager.getLogger(ResolvedMember::class)

        fun resolveGenerics(
            selfType: Type?, type: Type,
            genericNames: List<Parameter>,
            genericValues: ParameterList?
        ): Type {
            if (genericValues == null) return type
            return when (type) {
                is GenericType -> {
                    check(genericNames.size == genericValues.size) {
                        "Expected same number of generic names and generic values, got $genericNames vs $genericValues ($type)"
                    }
                    val idx = genericNames.indexOfFirst { it.name == type.name && it.scope == type.scope }
                    genericValues.getOrNull(idx) ?: type
                }
                is UnionType -> {
                    val types = type.types
                        .map { genericValues.resolveGenerics(selfType, it) }
                    unionTypes(types)
                }
                is AndType -> {
                    val types = type.types
                        .map { genericValues.resolveGenerics(selfType, it) }
                    andTypes(types)
                }
                is ClassType -> {
                    val typeArgs = type.typeParameters
                    if (typeArgs.isNullOrEmpty()) return type
                    val newTypeArgs = typeArgs.map { genericValues.resolveGenerics(selfType, it) }
                    if (false && typeArgs != newTypeArgs) {
                        LOGGER.info("Mapped types: $typeArgs -> $newTypeArgs")
                    }
                    ClassType(type.clazz, newTypeArgs)
                }
                NullType, UnknownType -> type
                is LambdaType -> {
                    val newSelfType = genericValues.resolveGenericsOrNull(selfType, type.selfType)
                    val newReturnType = genericValues.resolveGenerics(selfType, type.returnType)
                    val newParameters = type.parameters.map {
                        val newType = genericValues.resolveGenerics(selfType, it.type)
                        LambdaParameter(it.name, newType)
                    }
                    LambdaType(newSelfType, newParameters, newReturnType)
                }
                is SelfType, is ThisType -> selfType ?: run {
                    throw IllegalStateException("ThisType/SelfType missing... $type")
                }
                is TypeOfField -> {
                    val valueType = type.field.valueType
                    if (valueType != null) {
                        resolveGenerics(selfType, valueType, genericNames, genericValues)
                    } else {
                        val context = ResolutionContext(type.field.selfType, false, null, emptyMap())
                        type.field.resolveValueType(context)
                    }
                }
                is NotType -> resolveGenerics(selfType, type.type, genericNames, genericValues).not()
                else -> throw NotImplementedError("Resolve generics in $type (${type.javaClass.simpleName})")
            }
        }
    }
}