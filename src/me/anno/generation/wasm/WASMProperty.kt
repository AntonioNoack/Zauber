package me.anno.generation.wasm

import me.anno.zauber.ast.rich.Field

data class WASMProperty(val field: Field?, val wasmType: WASMType, val index: Int)