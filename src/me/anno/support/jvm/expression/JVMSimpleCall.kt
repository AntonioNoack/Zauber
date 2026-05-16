package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Specialization

class JVMSimpleCall(
    val dst: SimpleFieldExpr,
    val method: MethodLike,
    val self: SimpleFieldExpr,
    val specialization: Specialization,
    val valueParameters: List<SimpleFieldExpr>,
    val enableInheritance: Boolean,

    scope: Scope, origin: Long,
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = dst.type
}