package me.anno.generation.wasm.runtime

import me.anno.generation.wasm.FunctionType
import me.anno.generation.wasm.WASMOpcode
import me.anno.generation.wasm.WASMStruct
import me.anno.generation.wasm.WASMType
import me.anno.utils.NumberUtils.toInt
import me.anno.zauber.logging.LogManager

@Suppress("Since15")
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
            ?: throw IllegalStateException(
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
                ?: throw IllegalStateException("Missing implementation for $name")

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

    @Suppress("Since15")
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
                        LOGGER.warn("Dropping ${stack.removeAt(newStackSize)}")
                    }
                }

                is WASMInstruction.LocalGet -> stack.add(stack[call.local0 + instr.index])
                is WASMInstruction.LocalSet -> {
                    // todo check type
                    val value = stack.removeLast()
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

                is WASMInstruction.Br -> return instr

                is WASMInstruction.StructNewDefault -> {
                    val type = binary.types[instr.typeIndex] as WASMStruct
                    val fields = ArrayList<Any?>(type.properties.size)
                    for (i in type.properties.indices) {
                        fields.add(getDefaultValue(type.properties[i].wasmType))
                    }
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
        var instanceType = instance.type
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
        // for now, we only have binary functions
        check(stack.size - 2 >= call.stack0)
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

            WASMOpcode.I64_ADD -> stack.add(popI64() + popI64())
            WASMOpcode.I64_SUB -> stack.add(popI64() - popI64())
            WASMOpcode.I64_MUL -> stack.add(popI64() * popI64())
            WASMOpcode.I64_DIV_S -> stack.add(popI64(1) / popI64())
            WASMOpcode.I64_DIV_U -> stack.add(java.lang.Long.divideUnsigned(popI64(1), popI64()))

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