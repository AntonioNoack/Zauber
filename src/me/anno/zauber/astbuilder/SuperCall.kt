package me.anno.zauber.astbuilder

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.types.impl.ClassType

class SuperCall(
    val type: ClassType,
    val valueParams: List<NamedParameter>?,
    val delegate: Expression?
)