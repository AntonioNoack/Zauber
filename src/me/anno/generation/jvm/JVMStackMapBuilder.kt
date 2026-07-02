package me.anno.generation.jvm

import me.anno.generation.java.JavaSourceGenerator.Companion.resolveType
import me.anno.generation.jvm.VerificationTypeInfo.Companion.DoubleVariable
import me.anno.generation.jvm.VerificationTypeInfo.Companion.FloatVariable
import me.anno.generation.jvm.VerificationTypeInfo.Companion.IntegerVariable
import me.anno.generation.jvm.VerificationTypeInfo.Companion.LongVariable
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.types.impl.ClassType

class JVMStackMapBuilder(
    private val gen: JVMBytecodeGenerator,
    private val cp: ConstantPool,
    private val locals: JVMLocals,
    private val graph: SimpleGraph,
    private val code: JVMCodeBuilder
) {

    fun buildFrames(): List<StackMapFrame> {

        data class Pending(
            val pc: Int,
            val locals: List<VerificationTypeInfo>,
            val stack: List<VerificationTypeInfo>
        )

        val pending = ArrayList<Pending>()

        // normal CFG block frames
        for (i in 1 until graph.blocks.size) {
            val block = graph.blocks[i]
            val label = code.blockLabels[block]!!
            val pc = code.labelPc(label)
            pending.add(Pending(pc = pc, locals = buildLocals(), stack = emptyList()))
        }

        // synthetic branch labels
        for (frame in code.pendingFrames) {
            val pc = code.labelPc(frame.label)
            pending.add(Pending(pc = pc, locals = frame.locals, stack = frame.stack))
        }

        val deduplicated = LinkedHashMap<Int, Pending>()
        for (entry in pending) deduplicated[entry.pc] = entry
        val sorted = deduplicated.values.sortedBy { it.pc }

        // build final frames
        val result = ArrayList<StackMapFrame>()
        var previousPc = -1
        for (entry in sorted) {

            val offsetDelta = if (previousPc < 0) {
                entry.pc
            } else {
                entry.pc - previousPc - 1
            }

            previousPc = entry.pc

            result.add(
                StackMapFrame(
                    offsetDelta = offsetDelta,
                    locals = entry.locals,
                    stack = entry.stack
                )
            )
        }

        return result
    }

    private fun buildLocals(): List<VerificationTypeInfo> {
        val ordered = locals.orderedLocals
        return ordered.map { local ->
            typeOf(local.type, gen, cp)
        }
    }

    companion object {

        fun typeOf(
            type0: me.anno.zauber.types.Type,
            gen: JVMBytecodeGenerator, cp: ConstantPool
        ): VerificationTypeInfo {
            return when (JVMBytecodeGenerator.toJVMValueType(type0)) {
                JVMValueType.INT -> IntegerVariable
                JVMValueType.FLOAT -> FloatVariable
                JVMValueType.LONG -> LongVariable
                JVMValueType.DOUBLE -> DoubleVariable
                JVMValueType.REFERENCE -> {
                    val internalName = when (val type = resolveType(type0)) {
                        is ClassType -> gen.getJVMName(type)
                        else -> "java/lang/Object"
                    }
                    VerificationTypeInfo.ObjectVariable(cp.clazz(internalName))
                }
            }
        }
    }
}