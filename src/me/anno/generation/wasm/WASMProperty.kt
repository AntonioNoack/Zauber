package me.anno.generation.wasm

import me.anno.zauber.ast.rich.Field

class WASMProperty(val field: Field, val wasmType: WASMType, var offset: Int)