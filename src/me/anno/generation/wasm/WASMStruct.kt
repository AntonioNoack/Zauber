package me.anno.generation.wasm

import me.anno.zauber.ast.rich.Field

data class WASMStruct(
    val typeIndex: Int,
    val typeName: String,
    val properties: List<WASMProperty>,
    val isNullable: Boolean,
) : WASMType2() {
    private val mapping = properties.associateBy { it.field }

    fun getIndex(field: Field): Int {
        return mapping[field]!!.index
    }

    val type = WASMType.Ref(typeIndex, typeName, isNullable)
}