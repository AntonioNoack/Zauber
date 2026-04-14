package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleNumber(
    val dst: SimpleFieldExpr,
    val value: NumberExpression,
    scope: Scope, origin: Int
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}