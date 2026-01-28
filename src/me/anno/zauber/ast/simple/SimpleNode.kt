package me.anno.zauber.ast.simple

import me.anno.zauber.generation.c.CSourceGenerator.isValueType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleNode(val graph: SimpleGraph) {

    var isEntryPoint = false

    var branchCondition: SimpleField? = null
        set(value) {
            check(field == null)
            field = value
        }

    var ifBranch: SimpleNode? = null
        set(value) {
            check(field == null)
            field = value
            value!!.entryBlocks.add(this)
        }

    var elseBranch: SimpleNode? = null
        set(value) {
            check(field == null)
            field = value
            value!!.entryBlocks.add(this)
        }

    val entryBlocks = ArrayList<SimpleNode>()

    var nextBranch: SimpleNode?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }

    val blockId = graph.nodes.size
    val instructions = ArrayList<SimpleInstruction>()

    fun add(expr: SimpleInstruction) {
        instructions.add(expr)
    }

    fun field(type: Type, ownership: Ownership = getOwnership(type)): SimpleField =
        SimpleField(type, ownership, graph.numFields++, null)

    fun field(type: Type, scopeIfThis: Scope?): SimpleField =
        SimpleField(type, getOwnership(type), graph.numFields++, scopeIfThis)

    override fun toString(): String {
        val suffix = if (nextBranch == null) {
            "end"
        } else if (branchCondition != null) {
            "$branchCondition ? ${ifBranch?.blockId} : ${elseBranch?.blockId}"
        } else {
            "${nextBranch?.blockId}"
        }
        val prefix = "b${blockId}[$suffix]:"
        return instructions.joinToString("", prefix, "") { instr ->
            "\n  $instr"
        }
    }

    fun isEmpty(): Boolean {
        return branchCondition == null && ifBranch == null && elseBranch == null &&
                instructions.isEmpty()
    }

    fun nextOrSelfIfEmpty(graph: SimpleGraph): SimpleNode {
        if (isEmpty()) return this
        val next = graph.addNode()
        nextBranch = next
        return next
    }

    companion object {
        fun getOwnership(type: Type): Ownership {
            return if (type.isValueType()) Ownership.VALUE
            else Ownership.SHARED
        }
    }
}