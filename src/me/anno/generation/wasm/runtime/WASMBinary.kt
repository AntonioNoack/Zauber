package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.FunctionType
import me.anno.generation.wasm.WASMFuncTypeOrStruct
import me.anno.generation.wasm.WASMType

class WASMBinary {
    var numPages = 0
    var types: List<WASMFuncTypeOrStruct> = emptyList()
    var imports: List<Pair<String, FunctionType>> = emptyList()
    var functions: List<FunctionType> = emptyList()
    var globals: List<WASMType> = emptyList()
    var exports: Map<String, Int> = emptyMap()
    var code: List<WASMMethod> = emptyList()
}