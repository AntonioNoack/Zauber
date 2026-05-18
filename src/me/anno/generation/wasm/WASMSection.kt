package me.anno.generation.wasm

enum class WASMSection {
    CUSTOM,
    TYPE,
    IMPORT,
    FUNCTION,
    TABLE,
    MEMORY,
    GLOBAL,
    EXPORT,
    START,
    ELEMENT,
    CODE,
    DATA,
    DATA_COUNT,
    TAG
}