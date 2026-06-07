package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.*
import me.anno.utils.NumberUtils.toInt
import me.anno.zauber.logging.LogManager
import kotlin.math.max
import kotlin.math.min

class WASMRuntime(val binary: WASMBinary) {

    companion object {
        private val LOGGER = LogManager.getLogger(WASMRuntime::class)
    }

    val stack = ArrayList<Any?>()
    val globals = ArrayList<Any?>()

    val knownImports = HashMap<String, (List<Any?>) -> List<Any?>>()
    fun register(name: String, function: (List<Any?>) -> List<Any?>) {
        knownImports[name] = function
    }

    init {
        for (g in binary.globals) {
            globals.add(null)
        }
    }

    fun call(methodName: String, params: List<Any?>): List<Any?> {
        val funcIndex = binary.exports[methodName]
            ?: error(
                "'$methodName' is not an exported function, " +
                        "options: ${binary.exports.keys}"
            )
        val funcType = getFunctionType(funcIndex)
        check(funcType.params.size == params.size)
        stack.addAll(params)
        execute(funcIndex)
        return List(funcType.results.size) {
            stack.removeLast()
        }.asReversed()
    }

    fun getFunctionType(methodIndex: Int): FunctionType {
        return if (methodIndex < binary.imports.size) {
            binary.imports[methodIndex].second
        } else {
            binary.functions[methodIndex - binary.imports.size]
        }
    }

    fun execute(methodIndex: Int) {
        if (methodIndex < binary.imports.size) {

            val (name, type) = binary.imports[methodIndex]
            val params = List(type.params.size) {
                stack.removeLast()
            }.asReversed()

            val callable = knownImports[name]
                ?: error("Missing implementation for $name")

            val result = callable(params)
            check(result.size == type.results.size)

            stack.addAll(result)

        } else {

            val codeIndex = methodIndex - binary.imports.size
            val funcType = binary.functions[codeIndex]
            val body = binary.code[codeIndex]

            val call = WASMCall(
                stack.size + body.locals.size,
                stack.size - funcType.params.size // params are already on the stack
            )

            // initialize locals
            for (type in body.locals) {
                stack.add(getDefaultValue(type))
            }

            execute(call, body.instructions)

            // clear locals and params from the stack, result stays
            stack.subList(call.local0, call.stack0).clear()

        }
    }

    fun execute(call: WASMCall, instructions: List<WASMInstruction>): WASMInstruction? {
        for (i in instructions.indices) {
            when (val instr = instructions[i]) {

                is WASMInstruction.I32Const -> stack.add(instr.value)
                is WASMInstruction.I64Const -> stack.add(instr.value)
                is WASMInstruction.F32Const -> stack.add(instr.value)
                is WASMInstruction.F64Const -> stack.add(instr.value)
                is WASMInstruction.Simple -> executeSimple(call, instr.opcode)

                is WASMInstruction.Call -> {
                    val type = getFunctionType(instr.functionIndex)
                    check(stack.size - call.stack0 >= type.params.size) {
                        "Missing params: ${stack.size} - ${call.stack0} >= ${type.params.size}"
                    }
                    val stackSize = stack.size
                    execute(instr.functionIndex)
                    val newStackSize = stackSize + type.results.size - type.params.size
                    check(newStackSize >= 0)
                    while (stack.size > newStackSize) {
                        val dropPosition = newStackSize - type.results.size
                        LOGGER.warn("Dropping ${stack.removeAt(dropPosition)} from $stack")
                    }
                }

                is WASMInstruction.LocalGet -> stack.add(stack[call.local0 + instr.index])
                is WASMInstruction.LocalSet -> {
                    // todo check type
                    val value = stack.removeLast()
                    stack[call.local0 + instr.index] = value
                }
                is WASMInstruction.LocalTee -> {
                    // todo check type
                    val value = stack.last()
                    stack[call.local0 + instr.index] = value
                }

                is WASMInstruction.GlobalGet -> stack.add(globals[instr.index])
                is WASMInstruction.GlobalSet -> globals[instr.index] = stack.removeLast()

                is WASMInstruction.RefIsNull -> {
                    val last = stack.removeLast()
                    check(isRef(last))
                    stack.add((last == null).toInt())
                }
                is WASMInstruction.RefNull -> stack.add(null)
                is WASMInstruction.RefAsNonNull -> {
                    check(stack.last() is WASMInstance)
                }

                is WASMInstruction.If -> {
                    val condition = stack.removeLast() as Int
                    val branch = if (condition != 0) instr.ifBranch else instr.elseBranch
                    val result = execute(call, branch)
                    if (result is WASMInstruction.Br) return result.next()
                    if (result != null) return result
                }

                is WASMInstruction.Loop -> {
                    while (true) {
                        val result = execute(call, instr.body)
                        if (result is WASMInstruction.Br) {
                            if (result.depth == 0) continue // continue this loop
                            return result.next() // an outer loop was 'continued'
                        }
                        if (result != null) return result
                        else break // done
                    }
                }

                is WASMInstruction.Block -> {
                    val result = execute(call, instr.body)
                    if (result is WASMInstruction.Br) {
                        if (result.depth == 0) continue // continue instructions
                        return result.next() // an outer loop was 'continued'
                    } else {
                        if (result != null) return result
                        else continue
                    }
                }

                is WASMInstruction.Br -> return instr
                is WASMInstruction.BrTable -> {
                    val index = popI32()
                    val offset =
                        if (index < 0 || index >= instr.targets.size) instr.targets.last()
                        else instr.targets[index] // last target is default
                    // println("branch offset[$index]: $offset")
                    return WASMInstruction.br[offset]
                }

                is WASMInstruction.StructNewDefault -> {
                    val type = binary.types[instr.typeIndex] as WASMStruct
                    val fields = ArrayList<Any?>(type.properties.size)
                    for (i in type.properties.indices) {
                        fields.add(getDefaultValue(type.properties[i].wasmType))
                    }
                    stack.add(WASMInstance(type, fields))
                }
                is WASMInstruction.StructNew -> {
                    val type = binary.types[instr.typeIndex] as WASMStruct
                    val fields = ArrayList<Any?>(type.properties.size)
                    repeat(type.properties.size) {
                        fields.add(stack.removeLast())
                    }
                    fields.reverse()
                    stack.add(WASMInstance(type, fields))
                }
                is WASMInstruction.StructGet -> {
                    val struct = stack.removeLast() as WASMInstance
                    val expectedType = binary.types[instr.typeIndex] as WASMStruct
                    // validate type of instance (inheritance)
                    check(isInstanceOf(struct, expectedType)) {
                        "Expected $struct to be instance of $expectedType"
                    }
                    stack.add(struct.fields[instr.fieldIndex])
                }
                is WASMInstruction.StructSet -> {
                    val value = stack.removeLast()
                    val struct = stack.removeLast() as WASMInstance
                    val expectedType = binary.types[instr.typeIndex] as WASMStruct
                    // validate type of instance (inheritance)
                    check(isInstanceOf(struct, expectedType)) {
                        "Expected $struct to be instance of $expectedType"
                    }
                    // todo validate type of value
                    struct.fields[instr.fieldIndex] = value
                }

                is WASMInstruction.ArrayNewDefault -> {
                    val newSize = stack.removeLast() as Int
                    val arrayType = binary.types[instr.typeIndex] as WASMArray
                    val elementType = arrayType.elementType
                    val arrayInstance = when (elementType) {
                        WASMType.I32 -> IntArray(newSize)
                        WASMType.I64 -> LongArray(newSize)
                        WASMType.F32 -> FloatArray(newSize)
                        WASMType.F64 -> DoubleArray(newSize)
                        else -> arrayOfNulls<Any>(newSize)
                    }
                    stack.add(arrayInstance)
                }
                is WASMInstruction.ArrayGet -> {
                    val index = stack.removeLast() as Int
                    val element = when (val array = stack.removeLast()) {
                        is IntArray -> array[index]
                        is LongArray -> array[index]
                        is FloatArray -> array[index]
                        is DoubleArray -> array[index]
                        is Array<*> -> array[index]
                        else -> error("Expected array, got ${array?.javaClass?.simpleName}")
                    }
                    stack.add(element)
                }
                is WASMInstruction.ArraySet -> {
                    val value = stack.removeLast()
                    val index = stack.removeLast() as Int
                    val array = stack.removeLast()
                    @Suppress("UNCHECKED_CAST")
                    when (array) {
                        is IntArray -> array[index] = value as Int
                        is LongArray -> array[index] = value as Long
                        is FloatArray -> array[index] = value as Float
                        is DoubleArray -> array[index] = value as Double
                        is Array<*> -> (array as Array<Any?>)[index] = value
                        else -> error("Expected array, got ${array?.javaClass?.simpleName}")
                    }
                }

                WASMInstruction.End -> return null
                WASMInstruction.Return -> return instr
                WASMInstruction.Drop -> stack.removeLast()

                else -> throw NotImplementedError("Implement $instr")
            }
            check(stack.size >= call.stack0) { "Popped too many values" }
        }
        return null
    }

    fun isInstanceOf(instance: WASMInstance, type: WASMStruct): Boolean {
        var instanceType: WASMStructLike = instance.type
        while (true) {
            if (type.typeIndex == instanceType.typeIndex) return true
            instanceType = instanceType.superType ?: return false
        }
    }

    fun popI32() = stack.removeLast() as Int
    fun popI64() = stack.removeLast() as Long
    fun popF32() = stack.removeLast() as Float
    fun popF64() = stack.removeLast() as Double

    fun pop(n: Int) = stack.removeAt(stack.lastIndex - n)
    fun popI32(n: Int) = pop(n) as Int
    fun popI64(n: Int) = pop(n) as Long
    fun popF32(n: Int) = pop(n) as Float
    fun popF64(n: Int) = pop(n) as Double

    fun pushBool(b: Boolean) = stack.add(b.toInt())

    fun executeSimple(call: WASMCall, opcode: Int) {
        // handle unary operations
        check(stack.size - 1 >= call.stack0)
        when (opcode) {
            WASMOpcode.I32_EQZ -> pushBool(popI32() == 0)
            WASMOpcode.I64_EQZ -> pushBool(popI64() == 0L)

            WASMOpcode.I32_WRAP_I64 -> stack.add(popI64().toInt())
            WASMOpcode.I64_EXTEND_I32S -> stack.add(popI32().toLong())
            WASMOpcode.I64_EXTEND_I32U -> stack.add(popI32().toUInt().toLong())

            WASMOpcode.F32_DEMOTE_F64 -> stack.add(popF64().toFloat())
            WASMOpcode.F64_PROMOTE_F32 -> stack.add(popF32().toDouble())

            WASMOpcode.I32S_TRUNC_F32 -> stack.add(popF32().toInt())
            WASMOpcode.I32U_TRUNC_F32 -> stack.add(popF32().toUInt().toInt())
            WASMOpcode.I32S_TRUNC_F64 -> stack.add(popF64().toInt())
            WASMOpcode.I32U_TRUNC_F64 -> stack.add(popF64().toUInt().toInt())
            WASMOpcode.I64S_TRUNC_F32 -> stack.add(popF32().toLong())
            WASMOpcode.I64U_TRUNC_F32 -> stack.add(popF32().toULong().toLong())
            WASMOpcode.I64S_TRUNC_F64 -> stack.add(popF64().toLong())
            WASMOpcode.I64U_TRUNC_F64 -> stack.add(popF64().toULong().toLong())

            WASMOpcode.F32_CONVERT_I32S -> stack.add(popI32().toFloat())
            WASMOpcode.F32_CONVERT_I32U -> stack.add(popI32().toUInt().toFloat())
            WASMOpcode.F32_CONVERT_I64S -> stack.add(popI64().toFloat())
            WASMOpcode.F32_CONVERT_I64U -> stack.add(popI64().toULong().toFloat())
            WASMOpcode.F64_CONVERT_I32S -> stack.add(popI32().toDouble())
            WASMOpcode.F64_CONVERT_I32U -> stack.add(popI32().toUInt().toDouble())
            WASMOpcode.F64_CONVERT_I64S -> stack.add(popI64().toDouble())
            WASMOpcode.F64_CONVERT_I64U -> stack.add(popI64().toULong().toDouble())

            else -> executeSimpleBinary(call, opcode)
        }
    }

    fun executeSimpleBinary(call: WASMCall, opcode: Int) {
        // handle binary operations
        check(stack.size - 2 >= call.stack0) {
            "Opcode may not have enough space, 0x${opcode.toString(16)}"
        }
        when (opcode) {
            WASMOpcode.I32_EQ -> pushBool(popI32() == popI32())
            WASMOpcode.I32_NE -> pushBool(popI32() != popI32())
            WASMOpcode.I32_LT_S -> pushBool(popI32(1) < popI32())
            WASMOpcode.I32_LT_U -> pushBool(Integer.compareUnsigned(popI32(1), popI32()) < 0)
            WASMOpcode.I32_GT_S -> pushBool(popI32(1) > popI32())
            WASMOpcode.I32_GT_U -> pushBool(Integer.compareUnsigned(popI32(1), popI32()) > 0)
            WASMOpcode.I32_LE_S -> pushBool(popI32(1) <= popI32())
            WASMOpcode.I32_LE_U -> pushBool(Integer.compareUnsigned(popI32(1), popI32()) <= 0)
            WASMOpcode.I32_GE_S -> pushBool(popI32(1) >= popI32())
            WASMOpcode.I32_GE_U -> pushBool(Integer.compareUnsigned(popI32(1), popI32()) >= 0)

            WASMOpcode.I64_EQ -> pushBool(popI64() == popI64())
            WASMOpcode.I64_NE -> pushBool(popI64() != popI64())
            WASMOpcode.I64_LT_S -> pushBool(popI64(1) < popI64())
            WASMOpcode.I64_LT_U -> pushBool(java.lang.Long.compareUnsigned(popI64(1), popI64()) < 0)
            WASMOpcode.I64_GT_S -> pushBool(popI64(1) > popI64())
            WASMOpcode.I64_GT_U -> pushBool(java.lang.Long.compareUnsigned(popI64(1), popI64()) > 0)
            WASMOpcode.I64_LE_S -> pushBool(popI64(1) <= popI64())
            WASMOpcode.I64_LE_U -> pushBool(java.lang.Long.compareUnsigned(popI64(1), popI64()) <= 0)
            WASMOpcode.I64_GE_S -> pushBool(popI64(1) >= popI64())
            WASMOpcode.I64_GE_U -> pushBool(java.lang.Long.compareUnsigned(popI64(1), popI64()) >= 0)

            WASMOpcode.I32_ADD -> stack.add(popI32() + popI32())
            WASMOpcode.I32_SUB -> stack.add(popI32(1) - popI32())
            WASMOpcode.I32_MUL -> stack.add(popI32() * popI32())
            WASMOpcode.I32_DIV_S -> stack.add(popI32(1) / popI32())
            WASMOpcode.I32_DIV_U -> stack.add(Integer.divideUnsigned(popI32(1), popI32()))

            WASMOpcode.I32_AND -> stack.add(popI32() and popI32())
            WASMOpcode.I32_OR -> stack.add(popI32() or popI32())
            WASMOpcode.I32_XOR -> stack.add(popI32() xor popI32())

            WASMOpcode.I32_SHL -> stack.add(popI32(1) shl popI32())
            WASMOpcode.I32_SHR_S -> stack.add(popI32(1) shr popI32())
            WASMOpcode.I32_SHR_U -> stack.add(popI32(1) ushr popI32())
            WASMOpcode.I32_ROTL -> stack.add(popI32(1).rotateLeft(popI32()))
            WASMOpcode.I32_ROTR -> stack.add(popI32(1).rotateRight(popI32()))

            WASMOpcode.I64_ADD -> stack.add(popI64() + popI64())
            WASMOpcode.I64_SUB -> stack.add(popI64() - popI64())
            WASMOpcode.I64_MUL -> stack.add(popI64() * popI64())
            WASMOpcode.I64_DIV_S -> stack.add(popI64(1) / popI64())
            WASMOpcode.I64_DIV_U -> stack.add(java.lang.Long.divideUnsigned(popI64(1), popI64()))

            WASMOpcode.I64_AND -> stack.add(popI64() and popI64())
            WASMOpcode.I64_OR -> stack.add(popI64() or popI64())
            WASMOpcode.I64_XOR -> stack.add(popI64() xor popI64())
            WASMOpcode.I64_SHL -> stack.add(popI64(1) shl popI64().toInt())
            WASMOpcode.I64_SHR_S -> stack.add(popI64(1) shr popI64().toInt())
            WASMOpcode.I64_SHR_U -> stack.add(popI64(1) ushr popI64().toInt())
            WASMOpcode.I64_ROTL -> stack.add(popI64(1).rotateLeft(popI64().toInt()))
            WASMOpcode.I64_ROTR -> stack.add(popI64(1).rotateRight(popI64().toInt()))

            WASMOpcode.F32_LT -> pushBool(popF32(1) < popF32())
            WASMOpcode.F32_GT -> pushBool(popF32(1) > popF32())
            WASMOpcode.F32_LE -> pushBool(popF32(1) <= popF32())
            WASMOpcode.F32_GE -> pushBool(popF32(1) >= popF32())
            WASMOpcode.F32_MIN -> stack.add(min(popF32(), popF32()))
            WASMOpcode.F32_MAX -> stack.add(max(popF32(), popF32()))

            WASMOpcode.F64_LT -> pushBool(popF64(1) < popF64())
            WASMOpcode.F64_GT -> pushBool(popF64(1) > popF64())
            WASMOpcode.F64_LE -> pushBool(popF64(1) <= popF64())
            WASMOpcode.F64_GE -> pushBool(popF64(1) >= popF64())
            WASMOpcode.F64_MIN -> stack.add(min(popF64(), popF64()))
            WASMOpcode.F64_MAX -> stack.add(max(popF64(), popF64()))

            else -> error("Opcode 0x${opcode.toString(16)} not yet implemented")
        }
    }

    fun getDefaultValue(type: WASMType): Any? {
        return when (type) {
            WASMType.I32 -> 0
            WASMType.I64 -> 0L
            WASMType.F32 -> 0f
            WASMType.F64 -> 0.0
            else -> null
        }
    }

    fun isRef(e: Any?): Boolean {
        return when (e) {
            is Int, is Long,
            is Float, is Double -> false
            is WASMInstance, null -> true
            else -> error("Unknown type ${e.javaClass.simpleName}")
        }
    }
}