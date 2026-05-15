package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleThis
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMGraph(scope: Scope, origin: Long) : Expression(scope, origin) {

    val startBlock = addNode()
    val thisFields = HashMap<SimpleThis, SimpleFieldExpr>()

    private var numFields = 0

    fun field(type: Type) = SimpleFieldExpr(type, numFields++, scope, origin)
    fun addNode() = JVMBlockExpression(this, scope, origin)

    override fun clone(scope: Scope): Expression = this
    override fun toStringImpl(depth: Int): String = "JVMSimpleGraph"
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = throw NotImplementedError()
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun resolveReturnType(context: ResolutionContext): Type = throw NotImplementedError()

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
}