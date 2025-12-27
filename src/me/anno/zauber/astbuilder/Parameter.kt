package me.anno.zauber.astbuilder

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Parameter(
    val isVar: Boolean,
    val isVal: Boolean,
    val isVararg: Boolean,
    val name: String,
    val type: Type,
    val initialValue: Expression?,
    val scope: Scope,
    val origin: Int
) {
    override fun toString(): String {
        return "${if (isVar) "var " else ""}${if (isVal) "val " else ""}${scope.pathStr}.$name: $type${if (initialValue != null) " = $initialValue" else ""}"
    }

    fun clone(scope: Scope): Parameter {
        return Parameter(isVar, isVal, isVararg, name, type, initialValue?.clone(scope), scope, origin)
    }
}