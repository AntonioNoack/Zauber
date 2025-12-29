package me.anno.zauber.astbuilder

import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class Field(
    var declaredScope: Scope,
    val isVar: Boolean,
    val isVal: Boolean,
    val selfType: Type?, // may be null inside methods, owner is stack, kind of
    val name: String,
    var valueType: Type?,
    val initialValue: Expression?,
    val keywords: List<String>,
    val origin: Int
) {

    companion object {
        private val LOGGER = LogManager.getLogger(Field::class)
    }

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
        if (valueType != null) {
            return valueType
        }

        val initialValue = initialValue
        if (initialValue != null) {
            val newContext = context.withCodeScope(initialValue.scope)
            LOGGER.info("Resolving field using initialValue: $initialValue, codeScope: ${newContext.codeScope}, selfScope: ${newContext.selfScope}")
            return TypeResolution.resolveType(newContext, initialValue)
        }

        val getterExpr = getterExpr
        if (getterExpr != null) {
            val newContext = context
                .withCodeScope(getterExpr.scope)
                .withSelfType(selfType ?: context.selfType)
            return TypeResolution.resolveType(newContext, getterExpr)
        }

        throw IllegalStateException("Field $this has neither type, nor initial/getter")
    }

    override fun toString(): String {
        return toString(10)
    }

    fun toString(depth: Int): String {
        return if (initialValue == null || depth < 0) {
            "Field($selfType.$name)"
        } else {
            "Field($selfType.$name=${initialValue.toString(depth)})"
        }
    }

    fun moveToScope(newScope: Scope) {
        check(declaredScope.fields.remove(this))
        declaredScope = newScope
        newScope.addField(this)
    }
}