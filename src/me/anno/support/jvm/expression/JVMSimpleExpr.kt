package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext

abstract class JVMSimpleExpr(scope: Scope, origin: Int) : Expression(scope, origin) {
    override fun clone(scope: Scope): Expression = this
    override fun toStringImpl(depth: Int): String = this.javaClass.simpleName
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true // resolved yes, but not specialized yet
    override fun forEachExpression(callback: (Expression) -> Unit) {
        // only contains itself
    }
}