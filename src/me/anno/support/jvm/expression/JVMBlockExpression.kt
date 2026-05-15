package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

// todo we somehow need a mix of SimpleInstr and Expression...

class JVMBlockExpression(val graph: JVMGraph, scope: Scope, origin: Long) : Expression(scope, origin) {

    var ifBranch: JVMBlockExpression? = null
    var elseBranch: JVMBlockExpression? = null
    var branchCondition: SimpleFieldExpr? = null

    var nextBranch: JVMBlockExpression?
        get() = ifBranch
        set(value) {
            ifBranch = value
        }

    var endStack: List<SimpleFieldExpr>? = null

    val instructions = ArrayList<JVMSimpleExpr>()
    fun add(expr: JVMSimpleExpr) {
        instructions.add(expr)
    }

    fun field(type: Type) = graph.field(type)

    override fun resolveReturnType(context: ResolutionContext): Type {
        TODO("Not yet implemented")
    }

    override fun clone(scope: Scope): Expression {
        TODO("Not yet implemented")
    }

    override fun toStringImpl(depth: Int): String {
        TODO("Not yet implemented")
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        TODO("Not yet implemented")
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        TODO("Not yet implemented")
    }

    override fun splitsScope(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isResolved(): Boolean {
        TODO("Not yet implemented")
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        TODO("Not yet implemented")
    }

}