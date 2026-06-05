package me.anno.generation.wasm

import me.anno.zauber.ast.rich.member.Field

class WASMStruct(
    superType: WASMStruct?,
    typeIndex: Int, typeName: String,
    isNullable: Boolean,
) : WASMStructLike(
    superType, typeIndex, typeName, isNullable,
    WASMType.Ref(typeIndex, typeName, isNullable)
) {

    val properties = ArrayList<WASMProperty>()

    private val mapping by lazy {
        properties.associateBy { it.field }
    }

    fun getIndex(field: Field): Int {
        val property = mapping[field]
            ?: throw IllegalStateException("Missing $field in $typeName")
        return property.index
    }

    override fun toString(): String {
        return if (superType != null) {
            "WASMStruct('$typeName' extends '${superType.typeName}', $properties)"
        } else {
            "WASMStruct('$typeName', $properties)"
        }
    }
}