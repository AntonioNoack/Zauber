package me.anno.zauber.ast.reverse

import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleGetOrSetField
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleGetLocalField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.ast.simple.fields.SimpleSetLocalField
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ResolutionContext
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
        newClass: Scope,
        fields: Map<Field, Field>
    ): SimpleGraph {
        val methodScope = newClass.getOrPut("b${block.blockId}", ScopeType.METHOD)
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
        replaceSharedFieldsWithLocals(graph, newClass, fields)
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

    fun collectFields(graph: SimpleGraph, newClass: Scope): Map<Field, Field> {
        val fieldMap = HashMap<Field, Field>()
        for (i in graph.blocks.indices) {
            val block = graph.blocks[i]
            for (j in block.instructions.indices) {
                val expr = block.instructions[j]
                if (expr is SimpleGetOrSetField && expr.isLocalField()) {
                    val field = expr.field
                    fieldMap.getOrPut(field) {
                        newClass.addField(
                            null, false, field.isMutable, null, field.name,
                            field.resolveValueType(ResolutionContext.minimal),
                            field.initialValue, Flags.SYNTHETIC, field.origin
                        )
                    }
                }
            }
        }
        return fieldMap
    }

    /**
     * all shared "simple-fields" and "local-fields" must be replaced
     *
     * todo explicit SimpleGetExplicitSelf(),
     *  SimpleSetLocalField(), SimpleGetSimpleField()
     * */
    fun replaceSharedFieldsWithLocals(graph: SimpleGraph, newClass: Scope, fields: Map<Field, Field>) {
        // find which simple-fields are used in which graph parts

        val sharedFields = HashMap<SimpleField, Field>()
        val sharedOwner = HashMap<SimpleField, SimpleBlock>()

        val sharedLocalFields = HashMap<SimpleField, Field>()
        val sharedLocalOwner = HashMap<SimpleField, SimpleBlock>()

        fun handleField(block: SimpleBlock, entry: SimpleBlock, field: SimpleField, instr: SimpleInstruction) {

        }

        fun handleField(block: SimpleBlock, entry: SimpleBlock, field: LocalField, instr: SimpleInstruction) {

        }

        fun scanBlock(block: SimpleBlock, entry: SimpleBlock) {
            for (instr in block.instructions) {
                when (instr) {
                    is SimpleTailCall -> scanBlock(instr.toBeCalled, entry)
                    is SimpleBranch -> {
                        scanBlock(instr.ifTrue, entry)
                        scanBlock(instr.ifFalse, entry)
                    }
                    is SimpleLoop -> {
                        scanBlock(instr.body, entry)
                    }
                    is SimpleGetLocalField -> handleField(block, entry, instr.field, instr)
                    is SimpleSetLocalField -> handleField(block, entry, instr.field, instr)
                    else -> TODO("extract simple fields for ${instr.javaClass.simpleName}")
                }
                instr.listSimpleFieldsIn()
                instr.listSimpleFieldsOut()
            }
        }
        // todo first find, which fields are shared
        for (block in graph.blocks) {
            if (block.isEntryPoint) {
                scanBlock(block, block)
            }
        }
        // todo then replace them
    }

}