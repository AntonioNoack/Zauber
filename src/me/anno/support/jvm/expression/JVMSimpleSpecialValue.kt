package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleSpecialValue(
    val dst: SimpleFieldExpr,
    val value: SpecialValue,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}