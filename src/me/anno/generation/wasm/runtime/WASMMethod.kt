package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.WASMType

data class WASMMethod(val locals: List<WASMType>, val instructions: List<WASMInstruction>)
