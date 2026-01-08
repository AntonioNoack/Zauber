package me.anno.zauber.types

import me.anno.zauber.typeresolution.ParameterList

class Specialization(typeParameters: List<Type>) {
    val typeParameters: List<Type> =
        if (typeParameters is ParameterList) typeParameters.readonly()
        else typeParameters
}