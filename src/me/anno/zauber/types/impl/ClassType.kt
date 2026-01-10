package me.anno.zauber.types.impl

import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type

/**
 * A scope, but also with optional type arguments,
 * e.g. ArrayList, ArrayList<Int> or Map<Key, Value>
 * */
class ClassType(val clazz: Scope, typeParameters: ParameterList?) : Type() {

    constructor(clazz: Scope, typeParams: List<Type>?) : this(
        clazz, if (typeParams == null) null
        else createParamList(clazz, typeParams)
    )

    init {
        if (clazz.scopeType == ScopeType.ENUM_ENTRY_CLASS) {
            throw IllegalStateException("Classes should use the general enum, not the entries")
        }
    }

    val typeParameters: ParameterList? = if (
        typeParameters == null &&
        clazz.hasTypeParameters &&
        clazz.typeParameters.isEmpty()
    ) emptyParameterList() else typeParameters

    companion object {
        private fun createParamList(clazz: Scope, typeParams: List<Type>): ParameterList {
            check(clazz.hasTypeParameters) { "$clazz is missing type parameter definition" }
            check(clazz.typeParameters.size == typeParams.size) {
                "Incorrect number of typeParams for $clazz, expected ${clazz.typeParameters.size}, got ${typeParams.size}"
            }
            val result = ParameterList(clazz.typeParameters)
            for (i in typeParams.indices) {
                result.set(i, typeParams[i], InsertMode.READ_ONLY)
            }
            return result
        }
    }

    /*init {
        check(typeParameters == null || !typeParameters.containsNull()) {
            "At least one unknown type parameter: $typeParameters"
        }
    }*/

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
        val className =
            if (clazz.name == "Companion") clazz.pathStr
            else clazz.name
        var asString = className
        if (typeParameters == null) {
            asString += "<?>"
        } else if (typeParameters.isNotEmpty()) {
            asString += if (depth > 0) {
                typeParameters.toString("<", ">", depth)
            } else {
                "..."
            }
        }// else we know it's empty, because it's defined as such
        return asString
    }

}