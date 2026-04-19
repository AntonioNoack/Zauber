package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.CheckEqualsOp
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.UnresolvedFieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Types

object FieldGetterSetter {

    fun createValueField(
        field: Field, fieldName: String,
        scope: Scope, origin: Int,
    ): Field {
        return scope.addField(
            null, false, isMutable = false, /* todo we actually have a parameter */null,
            fieldName, field.valueType, field.initialValue ?: field.getterExpr,
            Flags.NONE, origin
        )
    }

    fun createBackingField(field: Field, scope: Scope, origin: Int): Field = scope.addField(
        field.selfType, field.explicitSelfType, isMutable = field.isMutable, null,
        "field", field.valueType, field.initialValue ?: field.getterExpr,
        Flags.SYNTHETIC, origin
    )

    fun ZauberASTBuilderBase.finishField(field: Field) {
        flags = 0
        val origin = field.origin
        if (field.getter == null) {
            pushScope(ScopeType.FIELD_GETTER, "${field.name}:get") { getterScope ->
                createGetterMethod0(field, null, getterScope, origin)
            }
        }
        if (field.isMutable && field.setter == null) {
            pushScope(ScopeType.FIELD_SETTER, "${field.name}:set") { setterScope ->
                createSetterMethod0(field, null, "value", setterScope, origin)
            }
        }
    }

    fun ZauberASTBuilderBase.createGetterMethod0(
        field: Field, expr0: Expression?,
        getterScope: Scope, origin: Int
    ): Method {
        val backingField = createBackingField(field, getterScope, origin)
        return createGetterMethod1(field, expr0, backingField, getterScope, origin)
    }

    fun ZauberASTBuilderBase.createGetterMethod1(
        field: Field, expr0: Expression?, backingField: Field,
        getterScope: Scope, origin: Int
    ): Method {
        val isInterface = getterScope.parent?.scopeType == ScopeType.INTERFACE
        val expr = expr0 ?: run {
            if (!isInterface) {
                val fieldExpr = FieldExpression(backingField, getterScope, origin)
                val valueExpr = if (field.flags.hasFlag(Flags.LATEINIT)) {
                    val nullExpr = SpecialValueExpression(SpecialValue.NULL, getterScope, origin)
                    val condition = CheckEqualsOp(
                        fieldExpr, nullExpr, byPointer = true, negated = false, null,
                        getterScope, origin
                    )
                    val ifScope = getterScope.generate("if", ScopeType.METHOD_BODY)
                    val elseScope = getterScope.generate("if", ScopeType.METHOD_BODY)
                    val debugInfoParam = StringExpression(field.name, ifScope, origin)
                    val throwExpr = CallExpression(
                        UnresolvedFieldExpression("throwJNE", shouldBeResolvable, ifScope, origin),
                        emptyList(), listOf(NamedParameter(null, debugInfoParam)), origin
                    )
                    IfElseBranch(condition, throwExpr, fieldExpr.clone(elseScope))
                } else fieldExpr
                ReturnExpression(valueExpr, null, getterScope, origin)
            } else null
        }

        val methodName = "get${field.name.capitalize()}"
        val method = Method(
            field.selfType, false, methodName, emptyList(), emptyList(),
            getterScope, field.valueType, emptyList(),
            expr, packFlags() or field.flags, origin
        )
        method.backedField = field
        method.backingField = backingField
        getterScope.selfAsMethod = method
        field.getterExpr = expr
        field.getter = method
        field.hasCustomGetter = expr0 != null
        return method
    }


    fun ZauberASTBuilderBase.createSetterMethod0(
        field: Field, expr0: Expression?, fieldName: String,
        setterScope: Scope, origin: Int,
    ): Method {
        val backingField = createBackingField(field, setterScope, origin)
        val valueField = createValueField(field, fieldName, setterScope, origin)
        return createSetterMethod1(field, expr0, backingField, valueField, setterScope, origin)
    }

    fun ZauberASTBuilderBase.createSetterMethod1(
        field: Field, expr0: Expression?,
        backingField: Field, valueField: Field,
        setterScope: Scope, origin: Int,
    ): Method {
        val isInterface = setterScope.parent?.scopeType == ScopeType.INTERFACE
        val expr = expr0 ?: run {
            if (!isInterface) {
                val backingExpr = FieldExpression(backingField, setterScope, origin)
                val valueExpr = FieldExpression(valueField, setterScope, origin)
                AssignmentExpression(backingExpr, valueExpr)
            } else null
        }

        val methodName = "set${field.name.capitalize()}"
        val parameter = Parameter(0, valueField.name, field.valueType ?: TypeOfField(field), setterScope, origin)
        val method = Method(
            field.selfType, false, methodName, emptyList(),
            listOf(parameter), setterScope,
            Types.Unit, emptyList(),
            expr, packFlags() or field.flags, origin
        )
        method.backedField = field
        method.backingField = backingField
        setterScope.selfAsMethod = method
        field.setter = method
        field.hasCustomSetter = expr0 != null
        return method
    }

}