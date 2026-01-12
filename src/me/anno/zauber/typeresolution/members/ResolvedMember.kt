package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

abstract class ResolvedMember<V>(
    val ownerTypes: ParameterList,
    val callTypes: ParameterList,
    val resolved: V,
    val context: ResolutionContext
) {

    abstract fun getTypeFromCall(): Type

    companion object {

        private val LOGGER = LogManager.getLogger(ResolvedMember::class)

        fun resolveGenerics(
            selfType: Type?, type: Type,
            genericNames: List<Parameter>,
            genericValues: ParameterList?
        ): Type {
            if (genericValues == null) return type

            check(genericNames.size == genericValues.size) {
                "Expected same number of generic names and generic values, got $genericNames vs $genericValues ($type)"
            }
            if (genericNames.size != genericValues.size) {
                LOGGER.warn("Expected same number of generic names and generic values, got ${genericNames.size} vs ${genericValues.size} ($type)")
                return type
            }
            return when (type) {
                is GenericType -> {
                    val idx = genericNames.indexOfFirst { it.name == type.name && it.scope == type.scope }
                    genericValues.getOrNull(idx) ?: type
                }
                is UnionType -> {
                    val types = type.types.map { partType ->
                        resolveGenerics(selfType, partType, genericNames, genericValues)
                    }
                    unionTypes(types)
                }
                is ClassType -> {
                    val typeArgs = type.typeParameters
                    if (typeArgs.isNullOrEmpty()) return type
                    val newTypeArgs = typeArgs.map { partType ->
                        resolveGenerics(selfType, partType, genericNames, genericValues)
                    }
                    if (typeArgs != newTypeArgs) {
                        LOGGER.info("Mapped types: $typeArgs -> $newTypeArgs")
                    }
                    ClassType(type.clazz, newTypeArgs)
                }
                NullType, UnknownType -> type
                is LambdaType -> {
                    val newSelfType = if (type.selfType != null) {
                        resolveGenerics(selfType, type.selfType, genericNames, genericValues)
                    } else null
                    val newReturnType = resolveGenerics(selfType, type.returnType, genericNames, genericValues)
                    val newParameters = type.parameters.map {
                        val newType = resolveGenerics(selfType, it.type, genericNames, genericValues)
                        LambdaParameter(it.name, newType)
                    }
                    LambdaType(newSelfType, newParameters, newReturnType)
                }
                is SelfType -> selfType ?: run {
                    LOGGER.warn("SelfType missing... ${type.scope}")
                    type.scope.typeWithoutArgs
                }
                is ThisType -> selfType ?: run {
                    LOGGER.warn("ThisType missing... ${type.javaClass}")
                    type.type
                }
                else -> throw NotImplementedError("Resolve generics in $type (${type.javaClass.simpleName})")
            }
        }
    }
}