package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.impl.ClassType

/**
 * type or value parameter
 * */
class Parameter(
    val index: Int,
    val isVar: Boolean,
    val isVal: Boolean,
    val isVararg: Boolean,
    val name: String,
    val type: Type,
    val defaultValue: Expression?,
    val scope: Scope,
    val origin: Int
) {

    init {
        if (isVararg) {
            check(type is ClassType)
            check(type.clazz == ArrayType.clazz)
            check(type.typeParameters?.size == 1)
        }
    }

    fun getOrCreateField(selfType: Type?, keywords: KeywordSet): Field {
        // automatically gets added to fieldScope
        val field = field ?: scope.addField(
            selfType, false, isMutable = isVar, this,
            name, type, defaultValue, keywords, origin
        )
        this.field = field
        return field
    }

    var field: Field? = null

    constructor(index: Int, name: String, type: Type, scope: Scope, origin: Int) :
            this(index, false, true, false, name, type, null, scope, origin)

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
            index, isVar, isVal, isVararg, name, type,
            defaultValue?.clone(scope), scope, origin
        )
    }

    fun shift(i: Int): Parameter {
        return Parameter(
            index + i, isVar, isVal, isVararg, name, type,
            defaultValue, scope, origin
        )
    }

}