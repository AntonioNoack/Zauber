package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.TypeExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Import
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.LambdaType

/**
 * Generates a lambda from the base, effectively being a::b -> { a.b(allParamsNeeded) }
 * */
class GetMethodFromValueExpression(
    val self: Expression,
    val name: String,
    val nameAsImport: List<Import>,
    origin: Int
) : Expression(self.scope, origin) {

    override fun toStringImpl(depth: Int): String {
        return "${self.toString(depth)}::$name"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
        val targetType = context.targetType
        if (targetType is LambdaType) {
            return targetType
        }

        TODO("ResolveReturnType($this, $context)")
    }

    override fun resolveImpl(context: ResolutionContext): Expression {
        val targetType = context.targetType
        if (targetType is LambdaType) {
            when (self) {
                is TypeExpression -> return resolveFromType(context, targetType, self.type)
                is UnresolvedFieldExpression -> {
                    // check if it is a type
                    val selfType = self.scope.resolveTypeOrNull(self.name, self.nameAsImport)
                    if (selfType != null) {
                        return resolveFromType(context, targetType, selfType)
                    } else {
                        val field = self.resolveField(context.withTargetType(null))
                        TODO("ResolveImpl($this, $context, ${self.javaClass.simpleName}, field $field)")
                    }
                }
                else -> {
                    TODO("ResolveImpl($this, $context, ${self.javaClass.simpleName})")
                }
            }
        }

        TODO("ResolveImpl($this, $context)")
    }

    private fun resolveFromType(context: ResolutionContext, targetType: LambdaType, selfType: Type): Expression {
        val isObject = selfType is ClassType && selfType.clazz.isObjectLike()
        if (isObject) {
            val tmpScope = scope.generate("lambda", ScopeType.LAMBDA)
            val variables = List(targetType.parameters.size) {
                val type = targetType.parameters[it].type
                val name = ('a' + it).toString()
                val field = tmpScope.addField(
                    null, false, isMutable = false, null,
                    name, type, null, Keywords.NONE, origin
                )
                LambdaVariable(type, field)
            }

            val base = TypeExpression(selfType, scope, origin)
            val valueParameters = variables.map {
                NamedParameter(null, FieldExpression(it.field, scope, origin))
            }
            return LambdaExpression(
                variables, tmpScope,
                NamedCallExpression(base, name, nameAsImport, null, valueParameters, scope, origin)
            ).resolve(context)

        } else {
            check(targetType.parameters.isNotEmpty()) {
                "Expected at least one parameter to generate $this for $targetType"
            }

            val tmpScope = scope.generate("lambda", ScopeType.LAMBDA)
            val variables = List(targetType.parameters.size) {
                val type = targetType.parameters[it].type.specialize(context.spec)
                println("specialized ${targetType.parameters[it].type} -> $type for $this, $context")
                val field = tmpScope.addField(
                    null, false, isMutable = false, null,
                    name, type, null, Keywords.NONE, origin
                )
                LambdaVariable(type, field)
            }
            val base = FieldExpression(variables[0].field, scope, origin)
            val valueParameters = variables.subList(1, variables.size).map {
                NamedParameter(null, FieldExpression(it.field, scope, origin))
            }
            return LambdaExpression(
                variables, tmpScope,
                NamedCallExpression(base, name, nameAsImport, null, valueParameters, scope, origin)
            ).resolve(context)
        }
    }

    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    // todo or if the resolved method has some...
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return true // base.hasLambdaOrUnknownGenericsType()
    }

    override fun needsBackingField(methodScope: Scope): Boolean = self.needsBackingField(methodScope)
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false

    override fun clone(scope: Scope) = GetMethodFromValueExpression(self.clone(scope), name, nameAsImport, origin)
    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(self)
    }

}