package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

/**
 * a macro inside a macro,
 * only supported at compile-time (or within the interpreter) ofc
 * */
class DynamicMacroExpression(
    val method: ResolvedMethod,
    val valueParameters: List<NamedParameter>,
    scope: Scope, origin: Int
) : Expression(scope, origin) {

    override fun isResolved(): Boolean = true
    override fun splitsScope(): Boolean = valueParameters.any { it.value.splitsScope() }

    override fun resolveReturnType(context: ResolutionContext): Type =
        method.resolved.resolveReturnType(context)

    override fun clone(scope: Scope) = DynamicMacroExpression(method, valueParameters, scope, origin)
    override fun toStringImpl(depth: Int): String = "DynamicMacro($method, $valueParameters)"

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        TODO("Not yet implemented")
    }

    // may be more complicated than that... we cannot really check method
    override fun needsBackingField(methodScope: Scope): Boolean =
        valueParameters.any { it.value.needsBackingField(methodScope) }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        for (param in valueParameters) {
            callback(param.value)
        }
    }
}