package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class JVMSimpleMultiArray(
    val dst: SimpleFieldExpr, val totalType: ClassType, val dimensions: List<SimpleFieldExpr>,
    scope: Scope, origin: Int
) : JVMSimpleExpr(scope, origin) {
    override fun resolveReturnType(context: ResolutionContext): Type = Types.Nothing
}