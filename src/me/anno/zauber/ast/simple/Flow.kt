package me.anno.zauber.ast.simple

import me.anno.zauber.Compile.root
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

data class Flow(val value: SimpleField, val block: SimpleNode) {
    override fun toString(): String {
        return "Flow(value=$value, block=${block.blockId})"
    }

    private fun joinFields(other: Flow, joinedBlock: SimpleNode): SimpleField {
        if (value == other.value) return value

        val joinedType = unionTypes(value.type, other.value.type)
        val joinedField = joinedBlock.field(joinedType)
        joinedBlock.add(SimpleMerge(joinedField, value.use(), other.value.use(), root, -1))
        return joinedField
    }

    companion object {

        fun Flow?.join(other: Flow?): Flow? {
            if (this == null) return other
            if (other == null) return this
            if (this == other) return this

            val joinedBlock = block.graph.addNode()
            block.nextBranch = joinedBlock
            other.block.nextBranch = joinedBlock
            return Flow(joinFields(other, joinedBlock), joinedBlock)
        }

        fun Flow?.join(other: SimpleField, otherNode: SimpleNode): Flow {
            if (this == null) return Flow(other, otherNode)

            val joinedBlock = block.graph.addNode()
            val joinedType = unionTypes(value.type, other.type)
            val joinedField = joinedBlock.field(joinedType)
            block.nextBranch = joinedBlock
            otherNode.nextBranch = joinedBlock
            joinedBlock.add(SimpleMerge(joinedField, value.use(), other.use(), root, -1))
            return Flow(joinedField, joinedBlock)
        }
    }
}