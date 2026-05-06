package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

abstract class CollectionType(val types: List<Type>): Type() {
    abstract fun withTypes(types: List<Type>): Type
}