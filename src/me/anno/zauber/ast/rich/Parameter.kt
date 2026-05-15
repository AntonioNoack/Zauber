package me.anno.zauber.ast.rich

import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

/**
 * type or value parameter
 * */
class Parameter(
    val index: Int,
    val mutability: ParameterMutability,
    val expansion: ParameterExpansion,
    val parameterType: ParameterType,
    val name: String,
    var type: Type,
    val defaultValue: Expression?,
    val scope: Scope,
    val origin: Long
) {

    val isVar get() = mutability == ParameterMutability.VAR
    val isVal get() = mutability == ParameterMutability.VAL
    val isConst get() = mutability == ParameterMutability.CONST
    val isVararg get() = expansion == ParameterExpansion.VARARG

    fun getOrCreateField(selfType: Type?, keywords: FlagSet): Field {
        // automatically gets added to fieldScope
        val field = field ?: scope.addField(
            selfType, selfType != null, isMutable = isVar, this,
            name, type, defaultValue, keywords, origin
        )
        this.field = field
        return field
    }

    var field: Field? = null

    constructor(
        index: Int, name: String, parameterType: ParameterType,
        type: Type, scope: Scope, origin: Long
    ) : this(
        index, ParameterMutability.DEFAULT, ParameterExpansion.NONE,
        parameterType, name, type, null, scope, origin
    )

    constructor(
        index: Int, name: String, parameterType: ParameterType, mutability: ParameterMutability,
        type: Type, scope: Scope, origin: Long
    ) : this(
        index, mutability, ParameterExpansion.NONE,
        parameterType, name, type, null, scope, origin
    )

    override fun toString(): String {
        return "${if (isVar) "var " else ""}${if (isVal) "val " else ""}${scope.pathStr}.$name: $type${if (defaultValue != null) " = $defaultValue" else ""}"
    }

    override fun equals(other: Any?): Boolean {
        return other is Parameter &&
                other.name == name &&
                other.type == type
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    fun clone(scope: Scope): Parameter {
        return Parameter(
            index, mutability, expansion, parameterType, name, type,
            defaultValue?.clone(scope), scope, origin
        )
    }
}