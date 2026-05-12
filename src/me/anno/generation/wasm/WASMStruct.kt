package me.anno.generation.wasm

import me.anno.zauber.ast.rich.Field

class WASMStruct(
    val classIndex: Int, val byteSize: Int,
    val fields: List<WASMProperty>
) {
    fun getOffset(field: Field): Int {
        val field = fields.firstOrNull { it.field == field }
            ?: throw IllegalStateException("Missing field $field in #$classIndex, $fields")
        return field.offset
    }
}