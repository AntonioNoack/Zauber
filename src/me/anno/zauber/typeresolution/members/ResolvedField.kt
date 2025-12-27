package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Field
import me.anno.zauber.astbuilder.expression.*
import me.anno.zauber.astbuilder.expression.constants.SpecialValue
import me.anno.zauber.astbuilder.expression.constants.SpecialValueExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType

// todo we don't need only the type-param-generics, but also the self-type generics...
class ResolvedField(ownerTypes: List<Type>, field: Field, callTypes: List<Type>, context: ResolutionContext) :
    ResolvedCallable<Field>(ownerTypes, callTypes, field, context) {

    companion object {
        fun filterTypeByScopeConditions(field: Field, type: Type, context: ResolutionContext): Type {
            // todo filter type based on scope conditions
            // todo branches that return Nothing shall be ignored, and their condition applies even after
            var type = type
            var scope = context.codeScope
            while (true) {
                val condition = scope.branchCondition
                if (condition != null) {
                    type = applyConditionToType(field, type, condition, context)
                }

                println("Scope-Condition[${scope.pathStr}]: $condition")
                scope = scope.parentIfSameFile ?: break
            }
            return type
        }

        fun applyConditionToType(field: Field, type: Type, expr: Expression, context: ResolutionContext): Type {
            return when (expr) {
                // todo as? and as should be compilable to if-else-branch
                is IsInstanceOfExpr -> {
                    if (exprIsField(field, expr.left, context)) {
                        expr.right
                        TODO()
                    } else type
                }
                is CheckEqualsOp -> {
                    val newType = when {
                        exprIsField(field, expr.left, context) -> getUniqueValueType(expr.right)
                        exprIsField(field, expr.right, context) -> getUniqueValueType(expr.left)
                        else -> null
                    }
                    if (newType != null) {
                        val newType2 = if (expr.negated) {
                            newType.not()
                        } else newType
                        andTypes(type, newType2)
                    } else type
                }
                else -> {
                    println("!Ignoring $expr for $field")
                    type
                }
            }
        }

        fun exprIsField(field: Field, expr: Expression, context: ResolutionContext): Boolean {
            return when (expr) {
                is NameExpression -> {
                    if (expr.name == field.name) {
                        val field2 = resolveField(context, expr.name, null)
                        field2?.resolved == field
                    } else false
                }
                is FieldExpression -> expr.field == field
                is NamedCallExpression,
                is CallExpression,
                is SpecialValueExpression -> false
                else -> TODO("Is $expr (${expr.javaClass.simpleName}) the same as $field?")
            }
        }

        fun getUniqueValueType(expr: Expression): Type? {
            return when (expr) {
                is SpecialValueExpression if expr.value == SpecialValue.NULL -> NullType
                is NamedCallExpression, is CallExpression -> null // we could check their return type...
                else -> TODO("Get unique value for $expr (${expr.javaClass.simpleName})")
            }
        }
    }

    init {
        val ownerNames = field.selfTypeTypeParams
        check(ownerNames.size == ownerTypes.size)
        check(field.typeParameters.size == callTypes.size)
    }

    fun getValueType(context: ResolutionContext): Type {
        println("getting value of $resolved in scope ${context.codeScope.pathStr}")

        val field = resolved
        val ownerNames = field.selfTypeTypeParams
        val context = context.withSelfType(field.selfType)

        val valueType = field.deductValueType(context)
        val forType = resolveGenerics(valueType, ownerNames, ownerTypes)
        val forCall = resolveGenerics(forType, field.typeParameters, callTypes)

        return filterTypeByScopeConditions(field, forCall, context)
    }

    override fun getTypeFromCall(): Type {
        val baseType = getValueType(this.context /* todo is this correct??? */)
        // this must be a fun-interface, and we need to get the return type of the call...
        //  luckily, there is only a single method, but unfortunately, we need the call parameters...
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "ResolvedField(field=$resolved)"
    }
}