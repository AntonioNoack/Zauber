package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnresolvedType

class Field(
    var codeScope: Scope,

    val selfType: Type?, // may be null inside methods (self is stack) and on package level (self is static)
    val explicitSelfType: Boolean,

    val isMutable: Boolean,
    var byParameter: Any?, // Parameter | LambdaParameter | null

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
        if (selfType is UnresolvedType) {
            throw IllegalStateException("$selfType must be resolved")
        }
        if (selfType is ClassType && !selfType.clazz.isClassType()) {
            throw IllegalStateException("$this has invalid selfType: $selfType")
        }
    }

    fun needsBackingField(): Boolean {
        val getterBody = getter?.body ?: return true
        if (getterBody.needsBackingField(getterBody.scope)) return true

        val setterBody = setter?.body ?: return false
        return setterBody.needsBackingField(setterBody.scope)
    }

    fun isBackingField(methodScope: Scope): Boolean {
        return name == "field" &&
                keywords.hasFlag(Keywords.SYNTHETIC) &&
                codeScope == methodScope
    }

    fun getBackedField(): Field? {
        if (name != "field") return null
        if (!keywords.hasFlag(Keywords.SYNTHETIC)) return null
        val scope = codeScope
        if (scope.scopeType != ScopeType.FIELD_GETTER &&
            scope.scopeType != ScopeType.FIELD_SETTER
        ) return null

        val method = scope.selfAsMethod
        return method?.backedField
    }

    var getter: Method? = null
    var setter: Method? = null

    var getterExpr: Expression? = null
    var hasCustomGetter = false
    var hasCustomSetter = false

    fun selfTypeTypeParams(givenSelfType: Type?): List<Parameter> =
        selfTypeToTypeParams(selfType, givenSelfType)

    var typeParameters: List<Parameter> = emptyList()

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

        if (name == "field") {
            // todo check if inside getter/setter, if so, get the real field
            val scope = getGetterOrSetterScope()
            if (scope != null) {
                val sam = scope.selfAsMethod!!
                val owner = scope.parent!!
                val matchingField = owner.fields.first { it.setter == sam || it.getter == sam }
                return TypeOfField(matchingField)
            }
        }

        throw IllegalStateException("Field $this (${resolveOrigin(origin)}) has neither type, nor initial/getter, cannot resolve type")
    }

    fun getGetterOrSetterScope(): Scope? {
        var scope = codeScope
        while (true) {
            when (scope.scopeType) {
                ScopeType.FIELD_GETTER,
                ScopeType.FIELD_SETTER -> return scope
                else -> scope = scope.parentIfSameFile ?: return null
            }
        }
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

    fun moveToScope(newScope: Scope) {
        check(codeScope.fields.remove(this)) { "Failed to remove field from scope" }
        codeScope = newScope
        newScope.addField(this)
    }

    fun isPrivate(): Boolean = keywords.hasFlag(Keywords.PRIVATE)

}