package me.anno.generation.wasm

object WASMOpcode {
    const val UNREACHABLE = 0x00
    const val NOP = 0x01
    const val BLOCK = 0x02
    const val LOOP = 0x03
    const val IF = 0x04
    const val ELSE = 0x05

    const val END = 0x0b
    const val RETURN = 0x0f

    const val I32_EQZ = 0x45
    const val I64_EQZ = 0x50

    const val CALL = 0x10

    const val LOCAL_GET = 0x20
    const val LOCAL_SET = 0x21
    const val LOCAL_TEE = 0x22 // set + get = tee

    const val GLOBAL_GET = 0x23
    const val GLOBAL_SET = 0x24

    const val I32_CONST = 0x41
    const val I64_CONST = 0x42
    const val F32_CONST = 0x43
    const val F64_CONST = 0x44

    const val DROP = 0x1a

    const val REF_IS_NULL = 0xd1

}