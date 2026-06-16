package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

/**
 * represents a stack element in JVM bytecode
 * */
class JVMSimpleField(
    val graph: JVMGraph, val type: Type, val id: Int,
    scope: Scope, origin: Long
) : Expression(scope, origin) {

    var allocation: JVMSimpleAllocateInstance? = null

    override fun resolveValueType(context: ResolutionContext): Type = type
    override fun clone(scope: Scope): Expression = this
    override fun toStringImpl(depth: Int): String = "${style("%$id", YELLOW)}[$type]"
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    fun toSimple(block: SimpleBlock): SimpleField {
        val graph0 = block.graph
        val fieldMapping = graph.fieldMappings
            .getOrPut(graph0.method0, ::HashMap)
        return fieldMapping.getOrPut(this) {
            graph0.field(type.specialize(graph0.method0)).apply {
                println("mapping ${this@JVMSimpleField} to $this")
            }
        }
    }

    fun use() = this

    override fun forEachExpression(callback: (Expression) -> Unit) {
        // no contents
    }

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {
        return flow0.withValue(toSimple(block0))
    }
}