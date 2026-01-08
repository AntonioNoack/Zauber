package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType

class Constructor(
    valueParameters: List<Parameter>,
    scope: Scope,
    val superCall: InnerSuperCall?,
    body: Expression?,
    keywords: List<String>,
    origin: Int
) : MethodLike(
    scope.parent!!.typeWithoutArgs,
    emptyList(), valueParameters,
    UnitType, scope, body, keywords, origin
) {

    override val selfType: ClassType
        get() = super.selfType as ClassType

    override fun toString(): String {
        return "new ${selfType.clazz.pathStr}($valueParameters) { ... }"
    }
}