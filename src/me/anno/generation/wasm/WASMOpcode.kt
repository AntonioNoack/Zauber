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

    const val I32_ADD = 0x6A
    const val I32_SUB = 0x6B
    const val I32_MUL = 0x6C
    const val I32_DIV_S = 0x6D
    const val I32_DIV_U = 0x6E
    const val I32_REM_S = 0x6F
    const val I32_REM_U = 0x70

    const val I64_ADD = 0x7C
    const val I64_SUB = 0x7D
    const val I64_MUL = 0x7E
    const val I64_DIV_S = 0x7F
    const val I64_DIV_U = 0x80
    const val I64_REM_S = 0x81
    const val I64_REM_U = 0x82

    const val F32_ADD = 0x92
    const val F32_SUB = 0x93
    const val F32_MUL = 0x94
    const val F32_DIV = 0x95
    const val F32_MIN = 0x96
    const val F32_MAX = 0x97
    const val F32_REM = 0x99

    const val F64_ADD = 0xA0
    const val F64_SUB = 0xA1
    const val F64_MUL = 0xA2
    const val F64_DIV = 0xA3
    const val F64_MIN = 0xA4
    const val F64_MAX = 0xA5
    const val F64_REM = 0xA7

    const val DROP = 0x1a

    const val REF_NULL = 0xd0
    const val REF_IS_NULL = 0xd1
    const val REF_AS_NON_NULL = 0xd4
    // there are also nullability-jumps

}