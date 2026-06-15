package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.types.Type

class JVMLocalField(val id: Int, val name: String, var type: Type) {
    override fun toString(): String {
        return "${style("#$id", YELLOW)}[${style("\"$name\"", GREEN)}: $type]"
    }
}