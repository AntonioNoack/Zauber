package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class Field(
    var codeScope: Scope,

    val selfType: Type?, // may be null inside methods (self is stack) and on package level (self is static)
    val explicitSelfType: Boolean,

    val isMutable: Boolean,
    val byParameter: Any?, // Parameter | LambdaParameter | null

    val name: String,
    var valueType: Type?,
    val initialValue: Expression?,
    val keywords: KeywordSet,
    val origin: Int
) {

    companion object {
        private val LOGGER = LogManager.getLogger(Field::class)
    }

    init {
        if (selfType is ClassType && !selfType.clazz.isClassType()) {
            throw IllegalStateException("$this has invalid selfType")
        }
    }

    var getter: Method? = null
    var setter: Method? = null

    var getterExpr: Expression? = null

    fun selfTypeTypeParams(givenSelfType: Type?): List<Parameter> =
        selfTypeToTypeParams(selfType, givenSelfType)

    var typeParameters: List<Parameter> = emptyList()

    init {
        codeScope.addField(this)
    }

    // due to multi-interface, there may be many of them
    var overriddenFields: List<Field> = emptyList()
    var overriddenBy: List<Field> = emptyList()

    fun resolveValueType(context: ResolutionContext): Type {
        val valueType = valueType
        if (valueType != null) {
            return valueType
        }

        val initialValue = initialValue
        if (initialValue != null) {
            LOGGER.info("Resolving field ${selfType}.${name} using initialValue: $initialValue, codeScope: ${initialValue.scope}, selfScope: ${context.selfScope}")
            return TypeResolution.resolveType(context, initialValue)
        }

        var getterExpr = getterExpr
        if (getterExpr is ReturnExpression) getterExpr = getterExpr.value
        if (getterExpr != null) {
            val newContext = context
                .withSelfType(selfType ?: context.selfType)
            return TypeResolution.resolveType(newContext, getterExpr)
        }

        throw IllegalStateException("Field $this has neither type, nor initial/getter")
    }

    override fun toString(): String {
        return toString(10)
    }

    fun toString(depth: Int): String {
        return if (initialValue == null) {
            "Field($selfType.$name)"
        } else {
            "Field($selfType.$name=${initialValue.toString(depth)})"
        }
    }

    val originalScope: Scope = codeScope
    fun moveToScope(newScope: Scope) {
        check(codeScope.fields.remove(this))
        codeScope = newScope
        newScope.addField(this)
    }

    fun isPrivate(): Boolean = keywords.hasFlag(Keywords.PRIVATE)
}