package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class SimpleField(val type: Type, var id: Int, val constantRef: Expression?) {

    var numReads = 0
    var mergeInfo: SimpleMerge? = null

    fun use(): SimpleField {
        numReads++
        return this
    }

    override fun toString(): String {
        return when {
            id >= 0 && constantRef != null -> "\"$constantRef\"%$id"
            constantRef != null -> "\"$constantRef\""
            id >= 0 && type is ClassType && type.clazz.isObjectLike() -> "[${type.clazz.pathStr}]%$id"
            type is ClassType && type.clazz.isObjectLike() -> "[${type.clazz.pathStr}]"
            else -> "%$id[$type]"
        }
    }
}