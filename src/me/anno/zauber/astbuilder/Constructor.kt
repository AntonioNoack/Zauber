package me.anno.zauber.astbuilder

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.impl.ClassType

class Constructor(
    val selfType: ClassType,
    val valueParameters: List<Parameter>,
    val innerScope: Scope,
    val superCall: InnerSuperCall?,
    val body: Expression?,
    val keywords: List<String>,
    val origin: Int
) {

    val typeParameters: List<Parameter> get() = emptyList()

    override fun toString(): String {
        return "new ${selfType.clazz.pathStr}($valueParameters) { ... }"
    }
}