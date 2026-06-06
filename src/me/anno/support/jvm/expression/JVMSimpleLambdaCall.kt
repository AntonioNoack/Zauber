package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

@Deprecated("Generate the classes instantly")
class JVMSimpleLambdaCall(
    val dst: SimpleFieldExpr,
    val parameters: List<SimpleFieldExpr>,
    val calledMethod: ResolvedMethod,
    val interfaceType: Type,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = interfaceType
}