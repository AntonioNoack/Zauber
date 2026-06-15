package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleCompare(
    val dst: JVMSimpleField,
    val p0: JVMSimpleField?, val p1: JVMSimpleField?,
    val type: CompareType,
    val tmp: JVMSimpleField,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $p0 $type $p1"
}