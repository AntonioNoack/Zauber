package me.anno.zauber.ast.simple

import me.anno.zauber.generation.c.CSourceGenerator.isValueType
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleBlock(val graph: SimpleGraph, scope: Scope, origin: Int) {

    var isEntryPoint = false

    var branchCondition: SimpleField? = null
    var ifBranch: SimpleBlock? = null
    var elseBranch: SimpleBlock? = null
    val entryBlocks = ArrayList<SimpleBlock>()

    var nextBranch: SimpleBlock?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }

    val blockId = graph.blocks.size
    val instructions = ArrayList<SimpleExpression>()

    fun add(expr: SimpleExpression) {
        instructions.add(expr)
    }

    fun field(type: Type, ownership: Ownership = getOwnership(type)): SimpleField =
        SimpleField(type, ownership, graph.numFields++, null)

    fun field(type: Type, scopeIfThis: Scope?): SimpleField =
        SimpleField(type, getOwnership(type), graph.numFields++, scopeIfThis)

    override fun toString(): String {
        return instructions.joinToString("\n")
    }

    fun execute(runtime: Runtime): BlockReturn? {
        return runtime.executeBlock(this)
    }

    fun isEmpty(): Boolean {
        return branchCondition == null && ifBranch == null && elseBranch == null &&
                instructions.isEmpty()
    }

    companion object {
        fun getOwnership(type: Type): Ownership {
            return if (type.isValueType()) Ownership.VALUE
            else Ownership.SHARED
        }
    }
}