package me.anno.zauber.types.impl

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NullableAnyType

/**
 * A scope, but also with optional type arguments,
 * e.g. ArrayList, ArrayList<Int> or Map<Key, Value>
 * */
class ClassType(val clazz: Scope, typeParameters: ParameterList?) : Type() {

    constructor(clazz: Scope, typeParameters: List<Type>?, origin: Int) : this(
        clazz, if (typeParameters == null) null
        else createParameterList(clazz, typeParameters, origin)
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

    fun withTypeParameters(typeParameters: List<Type>): ClassType {
        return ClassType(clazz, ParameterList(clazz.typeParameters, typeParameters))
    }

    fun withTypeParameter(typeParameter: Type): ClassType {
        return ClassType(clazz, ParameterList(clazz.typeParameters, listOf(typeParameter)))
    }

    companion object {

        /**
         * yes for compiling,
         * no for language server
         * */
        var strictMode = true

        fun createParameterList(clazz: Scope, typeParams: List<Type>, origin: Int): ParameterList {
            if (strictMode) {
                check(clazz.hasTypeParameters) {
                    "$clazz is missing type parameter definition, at ${resolveOrigin(origin)}"
                }
                check(clazz.typeParameters.size == typeParams.size) {
                    "Incorrect number of typeParams for $clazz, " +
                            "expected ${clazz.typeParameters.size}, " +
                            "got ${typeParams.size}, " +
                            "at ${resolveOrigin(origin)}, " +
                            "defined in ${clazz.fileName}"
                }
            }
            if (clazz.typeParameters.size == typeParams.size) {
                val result = ParameterList(clazz.typeParameters)
                for (i in typeParams.indices) {
                    result.set(i, typeParams[i], InsertMode.READ_ONLY)
                }
                return result
            } else {
                val fallbackGenerics = List(typeParams.size) {
                    Parameter(
                        it, ('A' + it).toString(),
                        NullableAnyType, clazz, -1
                    )
                }
                val result = ParameterList(fallbackGenerics)
                for (i in typeParams.indices) {
                    result.set(i, typeParams[i], InsertMode.READ_ONLY)
                }
                return result
            }
        }
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
        val className =
            if (clazz.scopeType == ScopeType.COMPANION_OBJECT || !clazz.isClassType()) clazz.pathStr
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