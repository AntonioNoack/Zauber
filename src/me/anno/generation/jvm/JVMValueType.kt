package me.anno.generation.jvm

enum class JVMValueType(val letter: Char) {
    INT('I'),
    LONG('J'),
    FLOAT('F'),
    DOUBLE('D'),
    REFERENCE(';')
}