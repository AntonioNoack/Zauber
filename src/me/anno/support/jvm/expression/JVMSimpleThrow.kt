package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleThrow(val value: SimpleFieldExpr, scope: Scope, origin: Long) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = Types.Nothing
    override fun toStringImpl(depth: Int): String = "${style("throw", ORANGE)} $value"
}