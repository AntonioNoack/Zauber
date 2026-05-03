package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class Constructor(
    valueParameters: List<Parameter>,
    scope: Scope,
    val superCall: InnerSuperCall?,
    body: Expression?,
    keywords: FlagSet,
    origin: Int
) : MethodLike(
    null, false,
    scope.typeParameters, valueParameters,
    Types.Unit, scope, "?", body, keywords, origin
) {

    val selfTypeI get() = ownerScope.typeWithArgs
    val classScope get() = ownerScope

    override fun toString(): String {
        return "new ${selfTypeI.clazz.pathStr}($valueParameters) { ... }"
    }
}