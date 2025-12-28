package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

abstract class ResolvedCallable<V>(
    val ownerTypes: List<Type>,
    val callTypes: List<Type>,
    val resolved: V,
    val context: ResolutionContext
) {

    abstract fun getTypeFromCall(): Type

    companion object {

        fun resolveGenerics(
            selfType: Type?, type: Type,
            genericNames: List<Parameter>,
            genericValues: List<Type>
        ): Type {
            if (genericNames.size != genericValues.size) {
                System.err.println("Expected same number of generic names and generic values, got ${genericNames.size} vs ${genericValues.size} ($type)")
            }
            return when (type) {
                is GenericType -> {
                    val idx = genericNames.indexOfFirst { it.name == type.name && it.scope == type.scope }
                    genericValues.getOrNull(idx) ?: type
                }
                is UnionType -> {
                    type.types.map { partType ->
                        resolveGenerics(selfType, partType, genericNames, genericValues)
                    }.reduce { a, b -> unionTypes(a, b) }
                }
                is ClassType -> {
                    val typeArgs = type.typeParameters ?: return type
                    println("old types: $typeArgs")
                    val newTypeArgs = typeArgs.map { partType ->
                        resolveGenerics(selfType, partType, genericNames, genericValues)
                    }
                    println("new types: $newTypeArgs")
                    ClassType(type.clazz, newTypeArgs)
                }
                NullType -> type
                is LambdaType -> {
                    LambdaType(type.parameters.map {
                        val newType = resolveGenerics(selfType, it.type, genericNames, genericValues)
                        LambdaParameter(it.name, newType)
                    }, resolveGenerics(selfType, type.returnType, genericNames, genericValues))
                }
                is SelfType -> selfType ?: run {
                    System.err.println("SelfType missing... ${type.scope}")
                    type.scope.typeWithoutArgs
                }
                else -> throw NotImplementedError("Resolve generics in $type")
            }
        }
    }
}