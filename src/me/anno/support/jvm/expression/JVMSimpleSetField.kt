package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleSetField(
    val self: SimpleFieldExpr,
    val field: Field,
    val value: SimpleFieldExpr,
    scope: Scope, origin: Int
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = Types.Unit
}