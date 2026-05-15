package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleCompare(
    val dst: SimpleFieldExpr,
    val p0: SimpleFieldExpr?, val p1: SimpleFieldExpr?,
    val type: CompareType,
    val tmp: SimpleFieldExpr,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}