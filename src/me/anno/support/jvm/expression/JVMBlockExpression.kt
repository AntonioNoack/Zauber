package me.anno.support.jvm.expression

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

// todo we somehow need a mix of SimpleInstr and Expression...

class JVMBlockExpression(val graph: JVMGraph, val id: Int, scope: Scope, origin: Long) : Expression(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(JVMBlockExpression::class)
    }

    var ifBranch: JVMBlockExpression? = null
        set(value) {
            if (field != null) inputs.remove(field)
            if (value != null) inputs.add(value)
            field = value
        }

    var elseBranch: JVMBlockExpression? = null
        set(value) {
            if (field != null) inputs.remove(field)
            if (value != null) inputs.add(value)
            field = value
        }

    var branchCondition: JVMSimpleField? = null
        set(value) {
            field = value
            if (id == 1 && value != null && value.id == 3 && "initSystemClassLoader" in graph.scope.pathStr) {
                LOGGER.warn("Condition", RuntimeException("Got condition $value in ${graph.scope} | $this"))
                // exitProcess(-1)
            }
        }

    var nextBranch: JVMBlockExpression?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }

    val inputs = ArrayList<JVMBlockExpression>()

    val startStacks = ArrayList<List<JVMSimpleField>>()
    var newStartStack: List<JVMSimpleField>? = null

    val instructions = ArrayList<JVMSimpleExpr>()
    fun add(expr: JVMSimpleExpr) {
        instructions.add(expr)
    }

    fun field(type: Type) = graph.field(type)

    override fun resolveValueType(context: ResolutionContext): Type {
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope): Expression {
        TODO("Not yet implemented")
    }

    fun idStr() = style("b$id", GREEN)

    val isEntryPoint get() = id == 0

    fun short(): StringBuilder {
        val builder = StringBuilder()
        builder.append(idStr()).append('[')

        if (isEntryPoint) builder.append("->|")

        if (nextBranch == null) {
            builder.append(StringStyles.style("end", StringStyles.RED))
        } else if (branchCondition != null) {
            builder.append(branchCondition).append(" ? ")
                .append(style("b${ifBranch!!.id}", GREEN)).append(" : ")
                .append(style("b${elseBranch!!.id}", GREEN))
        } else {
            builder.append(style("b${nextBranch!!.id}", GREEN))
        }
        builder.append(']')
        return builder
    }


    override fun toStringImpl(depth: Int): String {
        val builder = short()
        /*val or = onReturn
        if (or != null) builder.append('r').append(or.blockId)
        val ot = onThrow
        if (ot != null) builder.append('t').append(ot.handler.blockId)*/
        builder.append(':')
        for (instr in instructions) {
            builder.append("\n  ").append(instr)
        }
        return builder.toString()
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = throw NotImplementedError()
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (instr in instructions) {
            callback(instr)
        }
    }

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        var blockI = flow0
        for (instr in instructions) {
            val blockIv = blockI.value ?: return blockI
            // println("// Simplifying $instr")
            blockI = instr.simplify(context, blockIv.block, blockI, needsValue)
        }
        return blockI
    }

}