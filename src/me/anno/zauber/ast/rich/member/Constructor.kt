package me.anno.zauber.ast.rich.member

import me.anno.utils.StringStyles.LIGHT_ORANGE
import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.parameter.InnerSuperCall
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Types

class Constructor(
    valueParameters: List<Parameter>,
    scope: Scope,
    var superCall: InnerSuperCall?,
    body: Expression?,
    flags: FlagSet,
    origin: Long
) : MethodLike(
    null, false,
    scope.typeParameters, valueParameters,
    Types.Unit, scope, "new-${scope.parent}", body, flags, origin
) {

    val selfTypeI get() = ownerScope.typeWithArgs2
    val classScope get() = ownerScope

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append(style("new ", ORANGE))
        builder.append(style(selfTypeI.clazz.pathStr, LIGHT_ORANGE))
        appendValueParams(builder)
        return builder.toString()
    }
}