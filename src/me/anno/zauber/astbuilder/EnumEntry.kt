package me.anno.zauber.astbuilder

import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class EnumEntry(
    val name: String,
    val typeParameters: List<Type>?,
    val valueParameters: List<NamedParameter>,
    val scope: Scope,
    val origin: Int
)