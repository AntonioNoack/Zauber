package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.expression.AssignmentExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.FieldExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.UnitType

object FieldGetterSetter {

    private val LOGGER = LogManager.getLogger(FieldGetterSetter::class)

    fun ZauberASTBuilder.readGetter() {
        consume(TokenType.OPEN_CALL)
        consume(TokenType.CLOSE_CALL)

        val field = lastField!!
        if (tokens.equals(i, ":")) {
            i++
            field.valueType = readType(null, true)
            LOGGER.info("Defined lastField $field as ${field.valueType}")
        }
        pushScope(ScopeType.FIELD_GETTER, "${field.name}:get") { getterScope ->
            val origin = origin(i)
            val backingField = Field(
                getterScope, field.selfType, true, true,
                "field", field.valueType, field.initialValue ?: field.getterExpr,
                emptyList(), origin
            )
            val getterExpr = when {
                consumeIf("=") -> {
                    ReturnExpression(readExpression(), null, getterScope, origin)
                }
                tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                    pushBlock(ScopeType.EXPRESSION, null) {
                        readMethodBody()
                    }
                }
                else -> throw IllegalStateException("Expected = or {} after get() at ${tokens.err(i)}")
            }

            createGetterMethod(field, getterExpr, backingField, getterScope, origin)
        }
    }

    fun ZauberASTBuilder.readSetter() {
        val field = lastField!!
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            check(tokens.equals(++i, TokenType.NAME))

            val setterFieldName = tokens.toString(i++)
            check(setterFieldName != "field")

            if (LOGGER.enableDebug) LOGGER.debug("found set ${field.name}, $setterFieldName")
            check(tokens.equals(i++, TokenType.CLOSE_CALL))

            pushScope(ScopeType.FIELD_SETTER, "${field.name}:set") { setterScope ->
                val origin = origin(i)
                val backingField = createBackingField(field, setterScope, origin)
                val valueField = createValueField(field, setterFieldName, setterScope, origin)
                val setterExpr = when {
                    consumeIf("=") -> {
                        ReturnExpression(readExpression(), null, setterScope, origin)
                    }
                    tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                        pushBlock(ScopeType.EXPRESSION, null) {
                            readMethodBody()
                        }
                    }
                    else -> null
                }
                createSetterMethod(field, setterExpr, backingField, valueField, setterScope, origin)
            }
        }// else LOGGER.info("found set without anything else, ${field.name}")
    }

    private fun createValueField(
        field: Field, fieldName: String,
        scope: Scope, origin: Int,
    ): Field {
        return Field(
            scope, field.selfType, false, /* todo we actually have a parameter */null,
            fieldName, field.valueType, field.initialValue ?: field.getterExpr,
            emptyList(), origin
        )
    }

    private fun createBackingField(
        field: Field,
        scope: Scope, origin: Int
    ): Field {
        return Field(
            scope, field.selfType, field.isMutable, null,
            "field", field.valueType, field.initialValue ?: field.getterExpr,
            emptyList(), origin
        )
    }

    fun ZauberASTBuilder.finishLastField() {
        val field = lastField ?: return
        if (needsGetter(field)) {
            keywords.clear()
            val origin = field.origin
            if (field.getter == null) {
                pushScope(ScopeType.FIELD_GETTER, "${field.name}:get") { getterScope ->
                    val backingField = createBackingField(field, getterScope, origin)
                    createGetterMethod(field, null, backingField, getterScope, origin)
                }
            }
            if (field.isMutable && field.setter == null) {
                pushScope(ScopeType.FIELD_SETTER, "${field.name}:set") { setterScope ->
                    val backingField = createBackingField(field, setterScope, origin)
                    val valueField = createValueField(field, "__field", setterScope, origin)
                    createSetterMethod(field, null, backingField, valueField, setterScope, origin)
                }
            }
        }
        lastField = null
    }

    private fun needsGetter(field: Field): Boolean {
        return true // just to make our lives easier in testing
        if ("override" in field.keywords || "open" in field.keywords) return true // for virtual call resolution
        if (field.codeScope.scopeType == ScopeType.INTERFACE) return true // to grab the field
        if (field.codeScope.scopeType == ScopeType.OBJECT ||
            field.codeScope.scopeType == ScopeType.COMPANION_OBJECT
        ) return true // to ensure initialization
        return false
    }

    fun ZauberASTBuilder.createGetterMethod(
        field: Field, expr: Expression?, backingField: Field,
        getterScope: Scope, origin: Int
    ) {
        val isInterface = getterScope.parent?.scopeType == ScopeType.INTERFACE
        val expr = expr ?: if (needsGetter(field)) {
            if (!isInterface) {
                val fieldExpr = FieldExpression(backingField, getterScope, origin)
                ReturnExpression(fieldExpr, null, getterScope, origin)
            } else null
        } else return

        val methodName = "get${field.name.capitalize()}"
        val method = Method(
            field.selfType, methodName, emptyList(), emptyList(),
            getterScope, field.valueType, emptyList(),
            expr, packKeywords(), origin
        )
        method.backingField = backingField
        getterScope.selfAsMethod = method
        field.getterExpr = expr
        field.getter = method
    }

    fun ZauberASTBuilder.createSetterMethod(
        field: Field, expr: Expression?,
        backingField: Field, valueField: Field,
        setterScope: Scope, origin: Int,
    ) {
        val isInterface = setterScope.parent?.scopeType == ScopeType.INTERFACE
        val expr = expr ?: if (needsGetter(field)) {
            if (!isInterface) {
                val backingExpr = FieldExpression(backingField, setterScope, origin)
                val valueExpr = FieldExpression(valueField, setterScope, origin)
                AssignmentExpression(backingExpr, valueExpr)
            } else null
        } else return

        val methodName = "set${field.name.capitalize()}"
        val parameter = Parameter(valueField.name, field.valueType ?: TypeOfField(field), setterScope, origin)
        val method = Method(
            field.selfType, methodName, emptyList(),
            listOf(parameter), setterScope,
            UnitType, emptyList(),
            expr, packKeywords(), origin
        )
        method.backingField = backingField
        setterScope.selfAsMethod = method
        field.setter = method
    }

}