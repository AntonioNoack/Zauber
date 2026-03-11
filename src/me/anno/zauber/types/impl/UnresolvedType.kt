package me.anno.zauber.types.impl

import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type

class UnresolvedType(
    val className: String, val typeParameters: List<Type>?,
    val scope: Scope, val imports: List<Import>
) : Type() {

    override val resolved: Type by lazy { resolve() }

    override fun toStringImpl(depth: Int): String {
        return "¿$className?<$typeParameters>"
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other === NullType || other === UnknownType) return false
        if (other is UnresolvedType &&
            other.className == className &&
            other.typeParameters == typeParameters &&
            other.scope == scope
        ) return true
        return other is Type && resolved == ((other as? UnknownType)?.resolved ?: other)
    }

    override fun hashCode(): Int = className.hashCode()
}