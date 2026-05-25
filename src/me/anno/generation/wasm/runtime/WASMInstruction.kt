package me.anno.generation.wasm.runtime

import me.anno.utils.GrowingList

sealed interface WASMInstruction {

    data object Unreachable : WASMInstruction
    data object Nop : WASMInstruction

    data class Block(
        val blockType: Int,
        val body: List<WASMInstruction>
    ) : WASMInstruction

    data class Loop(
        val blockType: Int,
        val body: List<WASMInstruction>
    ) : WASMInstruction

    data class If(
        val blockType: Int,
        val ifBranch: List<WASMInstruction>,
        val elseBranch: List<WASMInstruction>
    ) : WASMInstruction

    data object Else : WASMInstruction
    data object End : WASMInstruction

    data class Br(val depth: Int) : WASMInstruction {
        fun next(): Br? {
            return if (depth == 0) null else br[depth - 1]
        }
    }

    class BrTable(val targets: IntArray) : WASMInstruction {
        override fun toString(): String {
            return "br_table ${targets.joinToString(", ")}"
        }
    }

    data object Return : WASMInstruction
    data class Call(val functionIndex: Int) : WASMInstruction
    data class CallIndirect(val functionType: Int) : WASMInstruction

    data class LocalGet(val index: Int) : WASMInstruction
    data class LocalSet(val index: Int) : WASMInstruction
    data class LocalTee(val index: Int) : WASMInstruction

    data class GlobalGet(val index: Int) : WASMInstruction
    data class GlobalSet(val index: Int) : WASMInstruction

    data class I32Const(val value: Int) : WASMInstruction
    data class I64Const(val value: Long) : WASMInstruction
    data class F32Const(val value: Float) : WASMInstruction
    data class F64Const(val value: Double) : WASMInstruction

    data object Drop : WASMInstruction

    data object RefIsNull : WASMInstruction
    data object RefAsNonNull : WASMInstruction
    data class RefNull(val typeIndex: Int) : WASMInstruction

    // generic op groups
    data class Simple(val opcode: Int) : WASMInstruction

    // GC
    data class StructNew(val typeIndex: Int) : WASMInstruction
    data class StructNewDefault(val typeIndex: Int) : WASMInstruction
    data class StructGet(val typeIndex: Int, val fieldIndex: Int) : WASMInstruction
    data class StructSet(val typeIndex: Int, val fieldIndex: Int) : WASMInstruction

    data class ArrayNewDefault(val typeIndex: Int) : WASMInstruction
    data class ArrayGet(val typeIndex: Int) : WASMInstruction
    data class ArraySet(val typeIndex: Int) : WASMInstruction

    companion object {
        val simple = Array(256) { opcode -> Simple(opcode) }
        val br = GrowingList(::Br)
    }
}