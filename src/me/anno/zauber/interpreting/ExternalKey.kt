package me.anno.zauber.interpreting

import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

data class ExternalKey(val scope: Scope, val name: String, val parameterTypes: List<Type>)