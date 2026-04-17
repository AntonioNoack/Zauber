package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

class JVMSimpleLambdaCall(
    val dst: SimpleFieldExpr,
    val parameters: List<SimpleFieldExpr>,
    val calledMethod: ResolvedMethod,
    val interfaceType: Type,
    scope: Scope, origin: Int
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = interfaceType
}