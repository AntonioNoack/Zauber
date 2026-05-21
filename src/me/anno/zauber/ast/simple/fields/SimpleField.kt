package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.SimpleMerge
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

/**
 * LLVM-style field, which is assigned exactly once. SimpleMergeInfo joins fields where necessary.
 * For languages, where you can assign to such a field from multiple locations, use .dst instead and ignore SimpleMergeInfo.
 * */
class SimpleField(
    val type: Type, var id: Int,
    // todo this should be converted to Instance, so we can use it at runtime (?)
    val constantRef: Expression?
) {

    var numReads = 0
    var mergeInfo: SimpleMerge? = null

    /**
     * todo use this, 0 = this, 1 = self, 2 = unit, 3+x = parameters
     * */
    var fromLocalField: LocalField? = null

    fun use(): SimpleField {
        numReads++
        return this
    }

    val dst: SimpleField
        get() {
            var dst = this
            while (true) {
                dst = dst.mergeInfo?.dst
                    ?: return dst
            }
        }

    override fun toString(): String {
        val idName = style("%$id", StringStyles.YELLOW)
        val typeColor = StringStyles.LINK
        return when {
            id >= 0 && constantRef is NumberExpression ->
                "${style("\"${constantRef.value}\"", StringStyles.BLUE)}$idName"
            id >= 0 && constantRef is StringExpression ->
                "${style("\"${constantRef.value}\"", StringStyles.GREEN)}$idName"
            id >= 0 && constantRef != null -> "\"$constantRef\"$idName"
            constantRef != null -> "\"$constantRef\""
            id >= 0 && type is ClassType && type.clazz.isObjectLike() ->
                "[${style(type.clazz.pathStr, typeColor)}]$idName"
            type is ClassType && type.clazz.isObjectLike() ->
                "[${style(type.clazz.pathStr, typeColor)}]"
            else -> "$idName[${style(type.toString(), typeColor)}]"
        }
    }
}