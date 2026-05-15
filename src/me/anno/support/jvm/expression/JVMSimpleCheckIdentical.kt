package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleCheckIdentical(
    val dst: SimpleFieldExpr,
    val p0: SimpleFieldExpr, val p1: SimpleFieldExpr,
    val negated: Boolean,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}