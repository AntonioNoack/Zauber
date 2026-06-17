package me.anno.support.jvm

import me.anno.support.jvm.expression.JVMBlockExpression
import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.types.Type

class JVMTryCatchBlock(
    val start: JVMBlockExpression,
    val end: JVMBlockExpression,
    val handler: JVMBlockExpression,
    val type: Type
) {
    override fun toString(): String {
        return "[${start.idStr()} ${style("until", ORANGE)} ${end.idStr()} | $type -> ${handler.idStr()}]"
    }
}