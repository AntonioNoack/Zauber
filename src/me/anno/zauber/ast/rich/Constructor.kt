package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.impl.ClassType

class Constructor(
    val valueParameters: List<Parameter>,
    val scope: Scope,
    val superCall: InnerSuperCall?,
    val body: Expression?,
    val keywords: List<String>,
    val origin: Int
) {

    val selfType: ClassType get() = scope.parent!!.typeWithoutArgs
    val typeParameters: List<Parameter> get() = emptyList()

    override fun toString(): String {
        return "new ${selfType.clazz.pathStr}($valueParameters) { ... }"
    }
}