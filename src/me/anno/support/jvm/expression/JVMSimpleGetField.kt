package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleGetField(
    val dst: SimpleFieldExpr,
    val self: SimpleFieldExpr,
    val field: Field,
    scope: Scope, origin: Int
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}