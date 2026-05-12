package me.anno.generation.wasm

enum class WASMType(val wasmName: String, val byteSize: Int) {
    I32("i32", 4),
    I64("i64", 8),
    F32("f32", 4),
    F64("f64", 8)
}