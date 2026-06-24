package me.anno.zauber.ast.simple.controlflow

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleMerge
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes

data class Flow(val value: SimpleField, val block: SimpleBlock) {
    override fun toString(): String {
        return "${style("b${block.id}", StringStyles.GREEN)}: $value"
    }

    private fun joinFields(other: Flow, joinedBlock: SimpleBlock): SimpleField {
        if (value == other.value) return value

        val joinedType = unionTypes(value.type, other.value.type)
        val joinedField = joinedBlock.field(joinedType)
        val ifField = value.use().dst
        val elseField = other.value.use().dst
        joinedBlock.add(SimpleMerge(joinedField, ifField, elseField, root, -1))
        return joinedField
    }

    companion object {

        fun Flow?.join(other: Flow?): Flow? {
            if (this == null) return other
            if (other == null) return this
            if (this == other) return this

            val joinedBlock = block.graph.addBlock()
            block.nextBranch = joinedBlock
            other.block.nextBranch = joinedBlock
            return Flow(joinFields(other, joinedBlock), joinedBlock)
        }

        fun Flow?.join(other: SimpleField, otherNode: SimpleBlock): Flow {
            if (this == null) return Flow(other, otherNode)

            val joinedBlock = block.graph.addBlock()
            val joinedType = unionTypes(value.type, other.type)
            val joinedField = joinedBlock.field(joinedType)
            block.nextBranch = joinedBlock
            otherNode.nextBranch = joinedBlock
            val ifField = value.use()
            val elseField = other.use()
            joinedBlock.add(SimpleMerge(joinedField, ifField, elseField, root, -1))
            return Flow(joinedField, joinedBlock)
        }
    }
}