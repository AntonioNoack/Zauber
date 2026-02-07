package me.anno.zauber.ast.simple

import me.anno.zauber.generation.c.CSourceGenerator.isValueType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleNode(val graph: SimpleGraph) {

    var isEntryPoint = false

    var branchCondition: SimpleField? = null
        set(value) {
            check(field == null || value == null)
            field = value
        }

    var ifBranch: SimpleNode? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    var elseBranch: SimpleNode? = null
        set(value) {
            unlinkTo(field)
            field = value
            linkTo(value)
        }

    var thrownField: SimpleField? = null

    // todo exits, return has exception = null, throw has exception = Throwable
    //  this must contain any catch-handler logic and finally-code
    // todo when a throw or return occurs, we must execute this logic
    var onThrow: SimpleNode? = null
    var onReturn: SimpleNode? = null

    val isBranch get() = branchCondition != null && ifBranch != elseBranch

    private fun linkTo(value: SimpleNode?) {
        value ?: return
        value.inputNodes.add(this)
        outputNodes.add(value)
    }

    private fun unlinkTo(value: SimpleNode?) {
        value ?: return
        value.inputNodes.remove(this)
        outputNodes.remove(value)
    }

    val inputNodes = ArrayList<SimpleNode>(4)
    val outputNodes = ArrayList<SimpleNode>(2)

    var nextBranch: SimpleNode?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }


    fun clear() {
        check(!isEntryPoint)
        removeLinks()
        instructions.clear()
    }

    fun removeLinks() {
        ifBranch = null
        elseBranch = null
        branchCondition = null
        inputNodes.clear()
        outputNodes.clear()
    }

    fun isOnlyInput(input: SimpleNode): Boolean {
        return !isEntryPoint && inputNodes.all { it == input }
    }

    fun isOnlyOutput(output: SimpleNode?): Boolean {
        return (output == ifBranch || ifBranch == null) && (output == elseBranch || elseBranch == null)
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
        val builder = StringBuilder()
        builder.append('b').append(blockId)
            .append('[')

        if (nextBranch == null) {
            builder.append("end")
        } else if (branchCondition != null) {
            builder.append(branchCondition).append(" ? ")
                .append(ifBranch?.blockId).append(" : ")
                .append(elseBranch?.blockId)
        } else {
            builder.append(nextBranch?.blockId)
        }

        builder.append(']')
        val or = onReturn
        if (or != null) builder.append('r').append(or.blockId)
        val ot = onThrow
        if (ot != null) builder.append('t').append(ot.blockId)
        builder.append(':')
        for (instr in instructions) {
            builder.append("\n  ").append(instr)
        }
        return builder.toString()
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