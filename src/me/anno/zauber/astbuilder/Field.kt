package me.anno.zauber.astbuilder

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Field(
    val declaredScope: Scope,
    val isVar: Boolean,
    val isVal: Boolean,
    val selfType: Type?, // may be null inside methods, owner is stack, kind of
    val name: String,
    var valueType: Type?,
    val initialValue: Expression?,
    val keywords: List<String>,
    val origin: Int
) {

    var privateGet = false
    var privateSet = false

    var getterExpr: Expression? = null

    var setterFieldName: String = "value"
    var setterExpr: Expression? = null

    val selfTypeTypeParams: List<Parameter>
        get() = selfTypeToTypeParams(selfType)

    var typeParameters: List<Parameter> = emptyList()

    init {
        declaredScope.addField(this)
    }

    fun deductValueType(context: ResolutionContext): Type {
        val valueType = valueType
        if (valueType != null) return valueType

        val initialValue = initialValue
        if (initialValue != null) return TypeResolution.resolveType(context, initialValue)

        val getterExpr = getterExpr
        if (getterExpr != null) {
            val newContext = context.withSelfType(selfType ?: context.selfType)
            return TypeResolution.resolveType(newContext, getterExpr)
        }

        throw IllegalStateException("Field $this has neither type, nor initial/getter")
    }

    override fun toString(): String {
        return "Field($selfType.$name=$initialValue)"
    }
}