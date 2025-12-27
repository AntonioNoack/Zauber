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
            type: Type,
            genericNames: List<Parameter>,
            genericValues: List<Type>
        ): Type {
            check(genericNames.size == genericValues.size) {
                "Expected same number of generic names and generic values, got ${genericNames.size} vs ${genericValues.size}"
            }
            if (genericValues.isEmpty()) return type
            return when (type) {
                is GenericType -> {
                    val idx = genericNames.indexOfFirst { it.name == type.name && it.scope == type.scope }
                    genericValues.getOrNull(idx) ?: type
                }
                is UnionType -> {
                    type.types.map { partType ->
                        resolveGenerics(partType, genericNames, genericValues)
                    }.reduce { a, b -> unionTypes(a, b) }
                }
                is ClassType -> {
                    val typeArgs = type.typeParameters ?: return type
                    println("old types: $typeArgs")
                    val newTypeArgs = typeArgs.map { partType ->
                        resolveGenerics(partType, genericNames, genericValues)
                    }
                    println("new types: $newTypeArgs")
                    ClassType(type.clazz, newTypeArgs)
                }
                NullType -> type
                is LambdaType -> {
                    LambdaType(type.parameters.map {
                        val newType = resolveGenerics(it.type, genericNames, genericValues)
                        LambdaParameter(it.name, newType)
                    }, resolveGenerics(type.returnType, genericNames, genericValues))
                }
                else -> throw NotImplementedError("Resolve generics in $type")
            }
        }
    }
}