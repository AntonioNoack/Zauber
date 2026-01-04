package me.anno.zauber.types.impl

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType

/**
 * Generic type, named 'name', defined in 'scope';
 * look at scope to find the type
 * */
class GenericType(val scope: Scope, val name: String) : Type() {

    val byTypeParameter: Parameter
        get() = scope.typeParameters.firstOrNull { it.name == name /*&& it.scope == scope -> automatically filtered for */ }
            ?: throw IllegalStateException("Missing generic parameter '$name' in ${scope.pathStr}")

    val superBounds: Type
        get() = byTypeParameter.type

    override fun toStringImpl(depth: Int): String {
        return if (superBounds == NullableAnyType) {
            "${scope.pathStr}.$name"
        } else {
            "(${scope.pathStr}.$name: ${superBounds.toString(depth)})"
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is GenericType &&
                other.scope == scope &&
                other.name == name
    }

    override fun hashCode(): Int {
        return scope.hashCode() * 31 + name.hashCode()
    }
}