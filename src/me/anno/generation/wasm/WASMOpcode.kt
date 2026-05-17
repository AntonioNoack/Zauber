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

    // i32 comparisons
    const val I32_EQZ = 0x45
    const val I32_EQ = 0x46
    const val I32_NE = 0x47
    const val I32_LT_S = 0x48
    const val I32_LT_U = 0x49
    const val I32_GT_S = 0x4A
    const val I32_GT_U = 0x4B
    const val I32_LE_S = 0x4C
    const val I32_LE_U = 0x4D
    const val I32_GE_S = 0x4E
    const val I32_GE_U = 0x4F

    // i64 comparisons
    const val I64_EQZ = 0x50
    const val I64_EQ = 0x51
    const val I64_NE = 0x52
    const val I64_LT_S = 0x53
    const val I64_LT_U = 0x54
    const val I64_GT_S = 0x55
    const val I64_GT_U = 0x56
    const val I64_LE_S = 0x57
    const val I64_LE_U = 0x58
    const val I64_GE_S = 0x59
    const val I64_GE_U = 0x5A

    // f32 comparisons
    const val F32_EQ = 0x5B
    const val F32_NE = 0x5C
    const val F32_LT = 0x5D
    const val F32_GT = 0x5E
    const val F32_LE = 0x5F
    const val F32_GE = 0x60

    // f64 comparisons
    const val F64_EQ = 0x61
    const val F64_NE = 0x62
    const val F64_LT = 0x63
    const val F64_GT = 0x64
    const val F64_LE = 0x65
    const val F64_GE = 0x66


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