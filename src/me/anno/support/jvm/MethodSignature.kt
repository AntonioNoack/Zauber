package me.anno.support.jvm

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.types.Type

data class MethodSignature(
    val typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    val returnType: Type
)