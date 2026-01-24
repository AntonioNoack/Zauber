package me.anno.zauber.types.specialization

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnknownType

class Specialization(typeParameters: ParameterList) {

    constructor(scope: ClassType) : this(
        ParameterList(
            scope.clazz.typeParameters,
            scope.typeParameters ?: emptyList()
        )
    )

    val typeParameters = typeParameters.readonly()
    val hash = typeParameters.hashCode() and 0x7fff_ffff

    fun isEmpty(): Boolean = typeParameters.isEmpty()
    fun isNotEmpty(): Boolean = typeParameters.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        return other is Specialization &&
                typeParameters == other.typeParameters
    }

    operator fun get(type: GenericType): Type? {
        val index = typeParameters.generics.indexOfFirst { it.name == type.name && it.scope == type.scope }
        if (index < 0) return null
        val resolved = typeParameters.getOrNull(index)
            ?: typeParameters.generics[index].type
        return if (type != resolved) resolved else null
    }

    operator fun get(type: Parameter): Type? {
        return get(GenericType(type.scope, type.name))
    }

    operator fun plus(other: Specialization): Specialization {
        // todo also resolve all recursive types,
        //  so if A is defined in B, use B and vice-versa
        // todo if something is defined twice, remove the duplicate and resolve conflicts...
        val typeParametersI = typeParameters.map { it.specialize(other) }
        val otherTypeParametersI = other.typeParameters.map { it.specialize(this) }
        return Specialization(typeParametersI + otherTypeParametersI)
    }

    fun indexOf(type: Type): Int {
        if (type !is GenericType) return -1
        val index = typeParameters.generics.indexOfFirst { it.name == type.name && it.scope == type.scope }
        if (index < 0) return -1
        val resolved = typeParameters.getOrNull(index)
            ?: typeParameters.generics[index].type
        return if (type != resolved) index else -1
    }

    operator fun contains(type: Type): Boolean {
        return indexOf(type) >= 0
    }

    override fun hashCode(): Int = hash

    fun createUniqueName(): String {
        val name = uniqueNames[this]
        if (name != null) return name

        val genName0 = typeParameters.indices.joinToString("_") {
            when (val type = typeParameters.getOrNull(it)) {
                is GenericType -> {
                    val selfAsMethod = type.scope.selfAsMethod
                    if (selfAsMethod != null) {
                        "${selfAsMethod.name}_${type.name}"
                    } else {
                        "${type.scope.name}_${type.name}"
                    }
                }
                NullType -> "null"
                null, UnknownType -> "?"
                // todo prefer a short name, so don't use full paths...
                else -> type.toString()
            }
                .replace("(ro)", "")
                .replace(".", "")
                .replace(":", "")
                .replace('<', 'X')
                .replace('>', 'x')
                .replace('(', 'X')
                .replace(')', 'x')
                .replace('[', 'X')
                .replace(']', 'x')
                .replace(", ", "_")
                .replace(",", "_")
                .replace("?", "$")
        }

        if (knownNames.add(genName0)) {
            uniqueNames[this] = genName0
            return genName0
        }

        for (i in 0 until 1000) {
            val genNameI = "$genName0$i"
            if (knownNames.add(genNameI)) {
                uniqueNames[this] = genNameI
                return genNameI
            }
        }
        throw IllegalStateException("Too many duplicates of $genName0")
    }

    override fun toString(): String {
        return List(typeParameters.generics.size) { index ->
            IndexedValue(index, typeParameters.generics[index].scope)
        }
            .groupBy { it.value }.entries
            .joinToString(", ", "{", "}") { (key, value) ->
                val indices = value.map { it.index }
                "${key.pathStr}: ${
                    indices.map { index ->
                        val name = typeParameters.generics[index].name
                        val type = typeParameters.getOrNull(index)
                        "$name=$type"
                    }
                }"
            }
    }

    companion object {
        private val uniqueNames = HashMap<Specialization, String>()
        private val knownNames = HashSet<String>()
        val noSpecialization = Specialization(ParameterList.emptyParameterList())
    }
}