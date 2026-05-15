package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class JVMSimpleAllocateInstance(
    val dst: SimpleFieldExpr,
    val selfType: ClassType,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = selfType
}