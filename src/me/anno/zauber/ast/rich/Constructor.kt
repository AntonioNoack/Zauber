package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.expression.Expression
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
    Types.Unit, scope, "?", body, flags, origin
) {

    val selfTypeI get() = ownerScope.typeWithArgs
    val classScope get() = ownerScope

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("new ")
        builder.append(selfTypeI.clazz.pathStr)
        valueParams(builder)
        return builder.toString()
    }
}