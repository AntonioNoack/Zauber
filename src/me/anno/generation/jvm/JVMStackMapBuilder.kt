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

        val result = ArrayList<StackMapFrame>()

        val blocks = graph.blocks
        if (blocks.isEmpty()) return result

        var previousPc = -1

        for (i in 1 until blocks.size) {

            val block = blocks[i]

            val label = code.blockLabels[block]!!
            val pc = code.labelPc(label)

            val offsetDelta = if (previousPc < 0) {
                pc
            } else {
                pc - previousPc - 1
            }

            previousPc = pc

            val frame = StackMapFrame(
                offsetDelta = offsetDelta,
                locals = buildLocals(),
                stack = emptyList()
            )

            result.add(frame)
        }

        return result
    }

    private fun buildLocals(): List<VerificationTypeInfo> {
        val ordered = locals.orderedLocals
        return ordered.map { local ->
            typeOf(local.type)
        }
    }

    private fun typeOf(type0: me.anno.zauber.types.Type): VerificationTypeInfo {
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