package me.anno.zauber.types.impl

import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * A scope, but also with optional type arguments,
 * e.g. ArrayList, ArrayList<Int> or Map<Key, Value>
 * */
class ClassType(val clazz: Scope, val typeParameters: List<Type>?) : Type() {

    init {
        check(typeParameters == null || typeParameters.none { it == null })
    }

    override fun equals(other: Any?): Boolean {
        return other is ClassType &&
                clazz == other.clazz &&
                (typeParameters == other.typeParameters ||
                        (classHasNoTypeParams() && typeParamsOrEmpty() == other.typeParamsOrEmpty()))
    }

    override fun hashCode(): Int {
        return clazz.pathStr.hashCode()
    }

    private fun typeParamsOrEmpty() = typeParameters ?: emptyList()

    fun classHasNoTypeParams(): Boolean {
        return clazz.hasTypeParameters && clazz.typeParameters.isEmpty()
    }

    override fun toStringImpl(depth: Int): String {
        val className = if (clazz.name == "Companion") clazz.pathStr else clazz.name
        var asString = className
        if (typeParameters == null || typeParameters.isNotEmpty()) {
            asString += if (depth > 0) {
                typeParameters?.joinToString(",", "<", ">") {
                    it.toString(depth)
                } ?: "<?>"
            } else {
                "..."
            }
        }// else we know it's empty, because it's defined as such
        return asString
    }

}