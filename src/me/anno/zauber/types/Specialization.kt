package me.anno.zauber.types

import me.anno.zauber.typeresolution.ParameterList

class Specialization(typeParameters: List<Type>) {
    val typeParameters: List<Type> =
        if (typeParameters is ParameterList) typeParameters.readonly()
        else typeParameters

    val hash = typeParameters.hashCode() and 0x7fff_ffff

    override fun equals(other: Any?): Boolean {
        return other is Specialization &&
                other.hash == hash &&
                typeParameters == other.typeParameters
    }

    override fun hashCode(): Int = hash
}