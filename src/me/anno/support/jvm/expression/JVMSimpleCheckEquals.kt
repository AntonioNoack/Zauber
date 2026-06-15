package me.anno.support.jvm.expression

import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

class JVMSimpleCheckEquals(
    val dst: SimpleFieldExpr,
    val p0: SimpleFieldExpr, val p1: SimpleFieldExpr,
    val negated: Boolean,
    val method: ResolvedMethod,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $p0 ${if (negated) "!=" else "=="} $p1"
}