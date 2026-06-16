package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.bold
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.ASTSimplifier.nativeNumbers
import me.anno.zauber.ast.simple.ASTSimplifier.unitInstance
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.Flow
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMGraph(scope: Scope, val isStatic: Boolean, origin: Long) : Expression(scope, origin) {

    init {
        check(scope.isMethodLike())
    }

    val blocks = ArrayList<JVMBlockExpression>()
    val startBlock = addNode()

    val localFields = ArrayList<JVMLocalField?>() // only contains 'this' in non-static methods
    val thisField = if (isStatic) null else getOrPutLocalField(0, "this", scope.parent!!.typeWithArgs)

    val fieldMappings = HashMap<Specialization, HashMap<JVMSimpleField, SimpleField>>()

    fun getOrPutLocalField(index: Int, name: String?, type: Type): JVMLocalField {
        repeat(index + 1 - localFields.size) {
            localFields.add(null)
        }

        var field = localFields[index]
        if (field != null) {
            if (field.type != type) {
                if (type == Types.Any && (field.type !in nativeNumbers && field.type != Types.Boolean)) {
                    // fine
                } else {
                    println("Incorrect type for field '${field.name}/$name' $index: $type != ${field.type}")
                }
            }
            return field
        }

        field = JVMLocalField(index, name ?: "#$index", type)
        localFields[index] = field
        return field
    }

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
            "  local: $localFields\n" +
            blocks.joinToString("") { block -> "$block\n" }

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        // todo lazy-discover them, so we know what is thrown-blocks(?)
        // todo how do we get the exception/return context? :/

        println("Converting graph: $this")

        val graph = block0.graph
        val unit = unitInstance(graph, this)

        val startBlocks = List(blocks.size) { index ->
            if (index == 0) block0 else graph.addBlock()
        }

        val simpleBlocks = blocks.mapIndexed { index, block ->
            val newBlock = startBlocks[index]
            val newFlow = FlowResult(Flow(unit, newBlock), null, null)
            newBlock to block.simplify(context, newBlock, newFlow, needsValue, contextExpr)
        }

        var finalFlow: FlowResult? = null
        for (i in blocks.indices) {
            val src = blocks[i]
            val result = simpleBlocks[i].second
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
                check(result.value == null)
                finalFlow = finalFlow?.joinWith(result) ?: result
            }
        }

        println("Converted graph: $graph")

        return finalFlow ?: ASTSimplifier.voidResult
    }
}