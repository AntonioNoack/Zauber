package me.anno.zauber.ast.reverse

import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.fields.*
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types

object GraphToClass {

    fun convertGraphToClass(graph: SimpleGraph) {
        val numEntryPoints = graph.blocks.count { it.isEntryPoint }
        if (numEntryPoints < 2) return

        println("Converting Graph to Class")
        println(graph)

        val origin = graph.method.origin

        // todo each entry-point becomes a method in a new class
        // todo start entry-point creates the class instance
        val clazz = graph.method.memberScope.generate("graphToClass", ScopeType.VIRTUAL_CLASS)
        clazz.setEmptyTypeParams()

        val constrScope = clazz.getOrCreatePrimaryConstructorScope()
        val constr = Constructor(emptyList(), constrScope, null, null, Flags.SYNTHETIC, origin)
        constrScope.selfAsConstructor = constr

        val simpleFields = collectSimpleFields(graph, clazz)
        val localFields = collectLocalFields(graph, clazz)

        val newEntryGraph = SimpleGraph(graph.method0)
        createEntryBlock(newEntryGraph, clazz, simpleFields, localFields)

        val entryGraphs = graph.blocks.filter { block -> block.isEntryPoint }
            .map { block -> createContentBlock(graph, block, clazz, simpleFields, localFields) }

        TODO("Register graphs as method bodies")

    }

    fun createEntryBlock(
        newGraph: SimpleGraph, clazz: Scope,
        simpleFields: Map<SimpleField, Field>,
        localFields: Map<LocalField, Field>
    ): SimpleBlock {
        // create clazz instance
        val instance = newGraph.field(clazz.typeWithArgs)
        val block = newGraph.startBlock
        val scope = newGraph.method.scope
        val origin = newGraph.method.origin
        block.instructions.add(
            SimpleAllocateInstance(
                instance, clazz.typeWithArgs2, emptyList(),
                Specialization(clazz, emptyParameterList()),
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
        simpleFields: Map<SimpleField, Field>,
        localFields: Map<LocalField, Field>
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
        replaceFields(block, simpleFields, localFields)
        replaceSharedFieldsWithLocals(graph, newClass, simpleFields, localFields)
        return graph
    }

    fun replaceFields(
        block: SimpleBlock,
        simpleFields: Map<SimpleField, Field>,
        localFields: Map<LocalField, Field>
    ) {
        for (i in block.instructions.indices) {
            val instr = block.instructions[i]
            if (instr is SimpleGSetLocalField) {
                val newField = localFields[instr.field] ?: continue
                TODO()
                // block.instructions[i] = instr.withField(newField)
            }
        }
    }

    /**
     * converts local fields into class fields
     * */
    fun collectLocalFields(graph: SimpleGraph, newClass: Scope): Map<LocalField, Field> {
        return graph.localFields.associateWith { field ->
            val origin = field.field?.origin ?: graph.method.origin
            val isMutable = true
            newClass.addField(
                null, false, isMutable, null, field.name,
                field.type, null, Flags.SYNTHETIC, origin
            )
        }
    }

    /**
     * converts simple fields into class fields
     * */
    fun collectSimpleFields(graph: SimpleGraph, newClass: Scope): Map<SimpleField, Field> {
        return graph.simpleFields.associateWith { field ->
            val origin = graph.method.origin
            val name = "tmp${field.id}"
            val isMutable = true
            newClass.addField(
                null, false, isMutable, null, name,
                field.type, null, Flags.SYNTHETIC, origin
            )
        }
    }

    /**
     * all shared "simple-fields" and "local-fields" must be replaced
     *
     * todo explicit SimpleGetExplicitSelf(),
     *  SimpleSetLocalField(), SimpleGetSimpleField()
     * */
    fun replaceSharedFieldsWithLocals(
        graph: SimpleGraph, newClass: Scope,
        simpleFields: Map<SimpleField, Field>,
        localFields: Map<LocalField, Field>
    ) {
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
                        if (instr.ifFalse != null) scanBlock(instr.ifFalse, entry)
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