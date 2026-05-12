package me.anno.generation.wasm

import me.anno.zauber.ast.rich.Field

class WASMStruct(
    val classIndex: Int, val byteSize: Int,
    val fields: List<WASMProperty>
) {
    fun getOffset(field: Field): Int {
        return fields.first { it.field == field }.offset
    }
}