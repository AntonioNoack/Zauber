package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.LambdaType

// todo we don't need only the type-param-generics, but also the self-type generics...
class ResolvedField(ownerTypes: ParameterList, field: Field, callTypes: ParameterList, context: ResolutionContext) :
    ResolvedMember<Field>(ownerTypes, callTypes, field, context) {

    companion object {
        private val LOGGER = LogManager.getLogger(ResolvedField::class)

        fun filterTypeByScopeConditions(field: Field, type: Type, context: ResolutionContext): Type {
            // todo filter type based on scope conditions
            // todo branches that return Nothing shall be ignored, and their condition applies even after
            var type = type
            var scope = context.codeScope
            while (true) {
                val conditions = scope.branchConditions
                for (i in conditions.indices) {
                    type = applyConditionToType(field, type, conditions[i], context)
                }

                LOGGER.info("Scope-Condition[${scope.pathStr}]: $conditions")
                scope = scope.parentIfSameFile ?: break
            }
            return type
        }

        fun applyConditionToType(field: Field, type: Type, expr: Expression, context: ResolutionContext): Type {
            return when (expr) {
                is IsInstanceOfExpr -> {
                    if (exprIsField(field, expr.instance, context)) {
                        andTypes(expr.type, type)
                    } else type
                }
                is CheckEqualsOp -> {
                    val newType = when {
                        exprIsField(field, expr.left, context) -> getUniqueValueType(context, expr.right)
                        exprIsField(field, expr.right, context) -> getUniqueValueType(context, expr.left)
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
                    LOGGER.info("!Ignoring $expr for $field")
                    type
                }
            }
        }

        fun exprIsField(field: Field, expr: Expression, context: ResolutionContext): Boolean {
            return when (expr) {
                is MemberNameExpression -> {
                    if (expr.name == field.name) {
                        val field2 = resolveField(context, expr.name, null, expr.origin)
                        field2?.resolved == field
                    } else false
                }
                is UnresolvedFieldExpression -> {
                    if (expr.name == field.name) {
                        val field2 = resolveField(context, expr.name, null, expr.origin)
                        field2?.resolved == field
                    } else false
                }
                is FieldExpression -> expr.field == field
                is NamedCallExpression,
                is CallExpression,
                is SpecialValueExpression -> false
                is ExpressionList -> exprIsField(field, expr.list.last(), context)
                is IfElseBranch -> expr.elseBranch != null && // unlikely
                        exprIsField(field, expr.ifBranch, context) &&
                        exprIsField(field, expr.elseBranch, context)
                is NumberExpression, is StringExpression -> false
                is DotExpression -> {
                    val baseType = expr.getBaseType(context)
                    val field = expr.resolveField(context, baseType)
                    field?.resolved == field
                }
                else -> throw NotImplementedError("Is $expr (${expr.javaClass.simpleName}) the same as $field?")
            }
        }

        /**
         * If value == expr, then value must have a special type:
         * */
        fun getUniqueValueType(context: ResolutionContext, expr: Expression): Type? {
            return TypeResolution.resolveType(context, expr)
        }
    }

    init {
        val ownerNames = field.selfTypeTypeParams(context.selfType)
        check(ownerNames.size == ownerTypes.size) {
            "Expected $ownerNames.size to be $ownerTypes.size for field $field"
        }
        check(field.typeParameters.size == callTypes.size)
    }

    fun getValueType(context: ResolutionContext): Type {
        LOGGER.info("Getting type of $resolved in scope ${context.codeScope.pathStr}")

        val field = resolved
        val ownerNames = field.selfTypeTypeParams(context.selfType)
        val selfType = field.selfType

        val valueType = field.resolveValueType(context)
        val forType = resolveGenerics(selfType, valueType, ownerNames, ownerTypes)
        val forCall = resolveGenerics(selfType, forType, field.typeParameters, callTypes)

        val context = context.withSelfType(field.selfType)
        return filterTypeByScopeConditions(field, forCall, context)
    }

    override fun getTypeFromCall(): Type {
        val baseType = getValueType(this.context /* todo is this correct??? */)
        if (baseType is LambdaType) {
            return baseType.returnType // easy-peasy :3
        }

        val funInterfaceType = resolveFunInterfaceType(baseType)
        val method = funInterfaceType.clazz.methods.first()
        // todo properly resolve the method...
        return method.returnType
            ?: TODO("Resolve return type of $method")
        // this must be a fun-interface, and we need to get the return type of the call...
        //  luckily, there is only a single method, but unfortunately, we need the call parameters...
    }

    fun resolveFunInterfaceType(type: Type): ClassType {
        val scope = typeToScope(type)
            ?: throw IllegalStateException("Cannot resolve scope from $type for fun-interface")
        val funScope = resolveFunInterfaceType(scope)
            ?: throw IllegalStateException("Could not find fun-interface in $scope")
        return funScope.typeWithoutArgs
    }

    // todo we could also support multiple interfaces, and choose the best match? good idea :)
    fun resolveFunInterfaceType(scope: Scope): Scope? {
        if (scope.keywords.hasFlag(Keywords.FUN_INTERFACE)) {
            return scope
        }
        for (superCall in scope.superCalls) {
            val byCall = resolveFunInterfaceType(superCall.type.clazz)
            if (byCall != null) return byCall
        }
        return null
    }

    override fun toString(): String {
        return "ResolvedField(field=$resolved)"
    }
}