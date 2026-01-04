package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression

/**
 * Used in calls: a named parameter
 * */
class NamedParameter(val name: String?, val value: Expression) {
    override fun toString(): String {
        return if (name != null) "$name=$value" else "$value"
    }

    fun toString(depth: Int): String {
        val valueStr = value.toString(depth)
        return if (name != null) "$name=$valueStr" else valueStr
    }
}