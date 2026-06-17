package me.anno.support.jvm

import me.anno.support.jvm.expression.JVMBlockExpression
import me.anno.zauber.types.Type

class JVMTryCatchBlock(
    val start: JVMBlockExpression,
    val end: JVMBlockExpression,
    val handler: JVMBlockExpression,
    val type: Type
)