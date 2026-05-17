package me.anno.generation.wasm

import me.anno.zauber.ast.rich.member.Field

data class WASMProperty(val field: Field?, val wasmType: WASMType, val index: Int)