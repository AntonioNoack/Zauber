package me.anno.zauber.interpreting

import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

data class ExternalKey(val ownerScope: Scope, val methodName: String, val valueParameterTypes: List<Type>)