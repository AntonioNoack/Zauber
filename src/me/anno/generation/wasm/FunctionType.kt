package me.anno.generation.wasm

data class FunctionType(val params: List<WASMType>, val results: List<WASMType>)