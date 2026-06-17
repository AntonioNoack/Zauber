package me.anno.support.jvm.expression

import me.anno.support.jvm.JVMTryCatchBlock
import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.bold
import me.anno.utils.StringStyles.style
import me.anno.utils.assertEquals
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.ASTSimplifier.unitInstance
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleMerge
import me.anno.zauber.ast.simple.controlflow.Flow
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.SimpleInstanceOf.Companion.createSimpleInstanceOf
import me.anno.zauber.ast.simple.expression.SimpleRename
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleGetLocalField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes

class JVMGraph(scope: Scope, val isStatic: Boolean, origin: Long) : Expression(scope, origin) {

    init {
        check(scope.isMethodLike())
    }

    val blocks = ArrayList<JVMBlockExpression>()
    val startBlock = addNode()

    val fieldMappings = HashMap<Specialization, HashMap<JVMSimpleField, SimpleField>>()

    val thisField = if (isStatic) null else field(scope.parent!!.typeWithArgs)

    // todo use these:
    //  all errors thrown between start and end (excl.)
    //  must be collected, instance-of-checked,
    //  and if need be, redirected to the handler
    //  else thrown
    val tryCatchBlocks = ArrayList<JVMTryCatchBlock>()

    val blockOrder = ArrayList<JVMBlockExpression>()

    private var numFields = 0

    fun field(type: Type): JVMSimpleField {
        return JVMSimpleField(this, type, numFields++, scope, origin)
    }

    fun addNode(): JVMBlockExpression {
        val expr = JVMBlockExpression(this, blocks.size, scope, origin)
        blocks.add(expr)
        return expr
    }

    override fun clone(scope: Scope): Expression = this
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = throw NotImplementedError()
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun resolveValueType(context: ResolutionContext): Type = Types.Unit

    override fun forEachExpression(callback: (Expression) -> Unit) {
        val unique = HashSet<JVMBlockExpression>()
        forEach(startBlock, unique, callback)
    }

    private fun forEach(
        block: JVMBlockExpression?, unique: HashSet<JVMBlockExpression>,
        callback: (Expression) -> Unit
    ) {
        if (block == null || !unique.add(block)) return

        for (instr in block.instructions) {
            callback(instr)
        }

        forEach(block.ifBranch, unique, callback)
        forEach(block.elseBranch, unique, callback)
    }

    override fun toStringImpl(depth: Int): String = "${bold("JVMGraph")}[$scope]:\n" +
            "  ${style("this", ORANGE)}: $thisField\n" +
            tryCatchBlocks.joinToString("") { "  $it\n" } +
            blocks.joinToString("") { block -> "$block\n" }

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        validateJVMBlocks()

        // todo lazy-discover them, so we know what is thrown-blocks(?)
        // todo how do we get the exception/return context? :/

        println("Converting graph: $this")

        val graph = block0.graph
        val unit = unitInstance(graph, this)

        val block0i = blocks.first()

        // assign this and parameters
        var offset = 0
        if (!isStatic) {
            val dst = block0i.newStartLocals!![0]!!.toSimple(graph)
            val field = graph.thisField!!
            block0.add(SimpleGetLocalField(dst, field, scope, origin))
            offset = 1
        }
        for (i in graph.method.valueParameters.indices) {
            val local = block0i.newStartLocals!![offset]!!
            // todo convert byte/short/char to int, where needed
            val dst = local.toSimple(graph)
            val field = graph.parameterFields[i]
            block0.add(SimpleGetLocalField(dst, field, scope, origin))
            offset += if (local.type == Types.Long || local.type == Types.Double) 2 else 1
        }

        val startBlocks = List(blocks.size) { graph.addBlock() }
        graph.startBlock.nextBranch = startBlocks[0]

        val simpleBlocks = blocks.mapIndexed { index, block ->
            val newBlock = startBlocks[index]
            val newFlow = FlowResult(Flow(unit, newBlock), null, null)
            newBlock to block.simplify(context, newBlock, newFlow, needsValue, contextExpr)
        }

        createMergeInstructions(graph, simpleBlocks)

        return linkBlocks(graph, simpleBlocks)
    }

    fun validateJVMBlocks() {
        for (block in blocks) {
            if (block.nextBranch != null) continue // all fine
            val lastInstr = block.instructions.lastOrNull()
            when (lastInstr) {
                is JVMSimpleReturn,
                is JVMSimpleThrow -> Unit
                else -> {
                    println("Broken Graph: $this")
                    error("$block cannot be an end-block")
                }
            }
        }
    }

    fun validateSimpleBlocks(graph: SimpleGraph, finalFlow: FlowResult?) {
        val returned = finalFlow?.returned?.block
        val thrown = finalFlow?.thrown?.block
        for (block in graph.blocks) {
            if (block.nextBranch != null) continue // all fine
            if (block == returned || block == thrown) continue // all fine
            val lastInstr = block.instructions.lastOrNull()
            when (lastInstr) {
                is SimpleReturn,
                is SimpleThrow -> Unit
                else -> {
                    println("Broken Graph: $graph")
                    error("$block cannot be an end-block")
                }
            }
        }
    }

    fun createMergeInstructions(graph: SimpleGraph, simpleBlocks: List<Pair<SimpleBlock, FlowResult>>) {
        for (i in blocks.indices) {
            val block = blocks[i]
            if (block.startStacks.isNotEmpty()) {
                mergeStacks(graph, simpleBlocks[i].first, block)
            }
            if (block.startLocals.isNotEmpty()) {
                mergeLocals(graph, simpleBlocks[i].first, block)
            }
        }
    }

    fun mergeStacks(
        graph: SimpleGraph, simpleBlock: SimpleBlock,
        block: JVMBlockExpression
    ) {
        val targetStack = block.newStartStack!!
        check(block.startStacks.all { it.size >= targetStack.size }) {
            println("Mismatch for $this")
            "Stack-size mismatch, expected $targetStack for ${block.idStr()}, but got ${block.startStacks}"
        }
        for (i in targetStack.indices) {
            val dst = targetStack[i].toSimple(graph)
            val candidates = block.startStacks
                .map { stack -> stack[stack.size - targetStack.size + i] }
            mergeFields(simpleBlock, dst, graph, candidates)
        }
    }

    fun mergeLocals(
        graph: SimpleGraph, simpleBlock: SimpleBlock,
        block: JVMBlockExpression
    ) {
        val targetStack = block.newStartLocals!!
        for ((i, ti) in targetStack) {
            val dst = ti.toSimple(graph)
            val candidates = block.startLocals
                .map { stack -> stack[i] }

            if (null in candidates) continue // incomplete fields cannot be merged
            @Suppress("UNCHECKED_CAST")
            mergeFields(simpleBlock, dst, graph, candidates as List<JVMSimpleField>)
        }
    }

    fun mergeFields(
        simpleBlock: SimpleBlock,
        dst: SimpleField,
        graph: SimpleGraph,
        candidates: List<JVMSimpleField>,
    ) {
        val candidatesI = candidates
            .map { stack -> stack.toSimple(graph) }
            .distinct() // probably not needed

        mergeFields(simpleBlock, dst, candidatesI)
    }

    fun mergeFields(
        simpleBlock: SimpleBlock,
        dst: SimpleField,
        candidates: List<SimpleField>,
    ) {
        if (candidates.isEmpty()) return

        if (candidates.size == 1) {
            val ci = candidates.first()
            if (ci == dst) return
            simpleBlock.add0(SimpleRename(dst, ci, scope, origin))
        } else {
            val created = ArrayList<SimpleInstruction>(candidates.size)
            val joinedType = unionTypes(candidates.map { it.type })

            var lhs = candidates.first()
            for (i in 1 until candidates.size) {
                val rhs = candidates[i]
                val tmp = simpleBlock.field(joinedType)
                created.add(SimpleMerge(tmp, lhs, rhs, scope, origin))
                lhs = tmp
            }

            created.add(SimpleRename(dst, lhs, scope, origin))
            simpleBlock.add0(created)
        }
    }

    fun distributeTryCatchBlocks() {
        val orderById = blockOrder.withIndex()
            .associate { it.value to it.index }
        for (tryCatchBlock in tryCatchBlocks) {
            val startId = orderById[tryCatchBlock.start]!!
            val endId = orderById[tryCatchBlock.end]!!
            check(endId > startId)

            for (i in startId until endId) {
                blockOrder[i].tryCatchBlocks.add(tryCatchBlock)
                // todo if true/false branch don't occur in blockOrder, they must be handled, too
            }
        }
    }

    fun linkBlocks(graph: SimpleGraph, simpleBlocks: List<Pair<SimpleBlock, FlowResult>>): FlowResult {

        distributeTryCatchBlocks()

        var finalFlow: FlowResult? = null
        for (i in blocks.indices) {
            val src = blocks[i]
            var result = simpleBlocks[i].second


            // todo we can do this later, if all following blocks
            //  have the same handlers...
            for (handler in src.tryCatchBlocks) {

                // todo handle exception as far as possible,
                //  and then modify result with the remainder
                val thrown = result.thrown ?: break
                val thrownValue = thrown.value

                // check stack contains exactly one element
                assertEquals(1, handler.handler.newStartStack!!.size) {
                    "Expected handler to have exactly one stack element"
                }

                val checkBlock = graph.addBlock()
                thrown.block.nextBranch = checkBlock

                val isInstance = graph.field(Types.Boolean)
                checkBlock.add(createSimpleInstanceOf(isInstance, thrownValue, handler.type, scope, origin))

                val ifBranch = graph.addBlock()
                val elseBranch = graph.addBlock()

                checkBlock.ifBranch = ifBranch
                checkBlock.elseBranch = elseBranch

                // todo we need explicit SimpleMerge(), if multiple blocks lead to the handler...
                val handlerField = handler.handler.newStartStack!![0].toSimple(graph)
                ifBranch.add(SimpleRename(handlerField, thrown.value, scope, origin))
                ifBranch.nextBranch = simpleBlocks[handler.handler.id].first

                result = result.withThrown(thrownValue, elseBranch)
            }

            val resultI = result.value?.block
            if (resultI != null) {
                if (src.branchCondition != null) {
                    resultI.branchCondition = src.branchCondition!!.toSimple(resultI)
                    resultI.ifBranch = simpleBlocks[src.ifBranch!!.id].first
                    resultI.elseBranch = simpleBlocks[src.elseBranch!!.id].first
                } else if (src.nextBranch != null) {
                    resultI.nextBranch = simpleBlocks[src.nextBranch!!.id].first
                } else error("We have a value but no next block?")
            } else {
                finalFlow = finalFlow?.joinWith(result) ?: result
            }
        }

        println("Converted graph: $graph")

        validateSimpleBlocks(graph, finalFlow)

        return finalFlow ?: ASTSimplifier.voidResult
    }
}