package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.WASMStruct

class WASMInstance(val type: WASMStruct, val fields: ArrayList<Any?>) {
    override fun toString(): String {
        return "${type.typeName}$fields"
    }
}