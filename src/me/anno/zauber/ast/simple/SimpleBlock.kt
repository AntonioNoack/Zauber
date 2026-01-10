package me.anno.zauber.ast.simple

import me.anno.zauber.generation.c.CSourceGenerator.isValueType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleBlock(val graph: SimpleGraph, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    val blockId = graph.blocks.size

    fun add(expr: SimpleExpression) {
        instructions.add(expr)
    }

    fun field(
        type: Type, ownership: Ownership =
            if (type.isValueType()) Ownership.VALUE
            else Ownership.SHARED
    ): SimpleField = SimpleField(type, ownership, graph.numFields++)

    val instructions = ArrayList<SimpleExpression>()

    override fun toString(): String {
        return instructions.joinToString("\n")
    }

    override fun execute(runtime: Runtime) {
        runtime.executeBlock(this)
    }

}