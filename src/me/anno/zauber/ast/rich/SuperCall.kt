package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.impl.ClassType

class SuperCall(
    val type: ClassType,
    val valueParams: List<NamedParameter>?,
    val delegate: Expression?
)