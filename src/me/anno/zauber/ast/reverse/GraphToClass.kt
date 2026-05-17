package me.anno.zauber.ast.reverse

import me.anno.generation.wasm.WASMSourceGenerator.Companion.isLocalField
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleGetOrSetField
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types

object GraphToClass {

    fun convertGraphToClass(graph: SimpleGraph) {
        val numEntryPoints = graph.blocks.count { it.isEntryPoint }
        if (numEntryPoints < 2) return

        println("Converting Graph to Class")
        println(graph)

        val origin = graph.method.origin

        // todo each entry-point becomes a method
        // todo start entry-point creates the class
        val clazz = graph.method.memberScope.generate("graphToClass", ScopeType.VIRTUAL_CLASS)
        clazz.setEmptyTypeParams()

        val constrScope = clazz.getOrCreatePrimaryConstructorScope()
        val constr = Constructor(emptyList(), constrScope, null, null, Flags.SYNTHETIC, origin)
        constrScope.selfAsConstructor = constr

        val fields = collectFields(graph, clazz)

        val newEntryGraph = SimpleGraph(graph.method0)
        createEntryBlock(newEntryGraph, clazz, fields)

        val entryGraphs = graph.blocks.filter { it.isEntryPoint }
            .map { createContentBlock(graph, it, clazz, fields) }


    }

    fun createEntryBlock(graph: SimpleGraph, clazz: Scope, fields: Map<Field, Field>): SimpleBlock {
        // create clazz instance
        val instance = graph.field(clazz.typeWithArgs)
        val block = graph.startBlock
        val scope = graph.method.scope
        val origin = graph.method.origin
        block.instructions.add(
            SimpleAllocateInstance(
                instance, clazz.typeWithArgs, emptyList(),
                scope, origin
            )
        )
        // todo assign all fields to null, where necessary
        // todo store method-parameters as class-fields
        // todo call entry-graph
        // todo go through all 'this'-fields, and store them, too
        return block
    }

    fun createContentBlock(
        graph: SimpleGraph,
        block: SimpleBlock,
        clazz: Scope,
        fields: Map<Field, Field>
    ): SimpleGraph {
        val methodScope = clazz.getOrPut("b${block.blockId}", ScopeType.METHOD)
        val origin = block.instructions.firstOrNull()?.origin ?: graph.method.origin
        val method = Method(
            null, false, methodScope.name,
            emptyList(), emptyList(), methodScope, Types.Unit,
            emptyList(), null, Flags.SYNTHETIC, origin
        )
        methodScope.selfAsMethod = method
        val graph = SimpleGraph(Specialization(method.scope, graph.method0.typeParameters))
        graph.startBlock.instructions.addAll(block.instructions)
        replaceFields(block, fields)
        if (true) TODO("We must also convert all shared SimpleFields [belongs to multiple methods] into fields...")
        return graph
    }

    fun replaceFields(block: SimpleBlock, fields: Map<Field, Field>) {
        for (i in block.instructions.indices) {
            val instr = block.instructions[i]
            if (instr is SimpleGetOrSetField) {
                val newField = fields[instr.field] ?: continue
                block.instructions[i] = instr.withField(newField)
            }
        }
    }

    fun collectFields(graph: SimpleGraph, clazz: Scope): Map<Field, Field> {
        val fieldMap = HashMap<Field, Field>()
        for (i in graph.blocks.indices) {
            val block = graph.blocks[i]
            for (j in block.instructions.indices) {
                val expr = block.instructions[j]
                if (expr is SimpleGetOrSetField && isLocalField(expr.self)) {
                    val field = expr.field
                    fieldMap.getOrPut(field) {
                        clazz.addField(
                            null, false, field.isMutable, null, field.name,
                            field.valueType!!, field.initialValue!!, Flags.SYNTHETIC, field.origin
                        )
                    }
                }
            }
        }
        return fieldMap
    }

}