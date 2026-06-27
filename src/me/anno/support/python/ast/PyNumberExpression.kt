package me.anno.support.python.ast

import me.anno.support.python.ast.PythonASTBuilder.Companion.pythonInstanceType
import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class PyNumberExpression(val value: String, scope: Scope, origin: Long) : Expression(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = pythonInstanceType
    override fun clone(scope: Scope): Expression = PyNumberExpression(value, scope, origin)

    override fun toStringImpl(depth: Int): String {
        return "Py'${style(value, StringStyles.DARK_BLUE)}"
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true

    override fun forEachExpression(callback: (Expression) -> Unit) {
    }
}