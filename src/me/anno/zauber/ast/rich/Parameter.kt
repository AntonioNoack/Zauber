package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

/**
 * type or value parameter
 * */
class Parameter(
    val isVar: Boolean,
    val isVal: Boolean,
    val isVararg: Boolean,
    val name: String,
    val type: Type,
    val defaultValue: Expression?,
    val scope: Scope,
    val origin: Int
) {

    constructor(name: String, type: Type, scope: Scope, origin: Int) :
            this(false, true, false, name, type, null, scope, origin)

    override fun toString(): String {
        return "${if (isVar) "var " else ""}${if (isVal) "val " else ""}${scope.pathStr}.$name: $type${if (defaultValue != null) " = $defaultValue" else ""}"
    }

    fun clone(scope: Scope): Parameter {
        return Parameter(isVar, isVal, isVararg, name, type, defaultValue?.clone(scope), scope, origin)
    }
}