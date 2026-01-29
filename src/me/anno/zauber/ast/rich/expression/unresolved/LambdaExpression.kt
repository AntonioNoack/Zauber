package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.SuperCall
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.resolved.ResolvedCallExpression
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.members.ResolvedConstructor
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.LambdaType

class LambdaExpression(
    var variables: List<LambdaVariable>?,
    val bodyScope: Scope,
    val body: Expression,
) : Expression(bodyScope, body.origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(LambdaExpression::class)
    }

    override fun toStringImpl(depth: Int): String {
        return "LambdaExpr(${variables ?: "?"} -> ${body.toString(depth)})"
    }

    // scope cannot be changed that easily, like with branches and loops
    override fun clone(scope: Scope) = LambdaExpression(variables, bodyScope, body.clone(bodyScope))

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = true
    override fun needsBackingField(methodScope: Scope): Boolean = body.needsBackingField(methodScope)
    override fun isResolved(): Boolean = false
    override fun splitsScope(): Boolean = false // I don't think so

    override fun resolveType(context: ResolutionContext): Type {
        LOGGER.info("Handling lambda expression... target: ${context.targetType}")
        val bodyContext = context
            .withTargetType(null)
        when (val targetLambdaType = context.targetType) {
            is LambdaType -> {
                // automatically add it...
                if (variables == null) {
                    variables = when (val size = targetLambdaType.parameters.size) {
                        0 -> emptyList()
                        1 -> {
                            // define 'it'-parameter in the scope
                            val param0 = targetLambdaType.parameters[0]
                            val type = param0.type
                            val autoParamName = "it"
                            LOGGER.info("Inserting $autoParamName into lambda automatically, type: $type")
                            val field = bodyScope.fields.firstOrNull { it.name == autoParamName } ?: bodyScope.addField(
                                null, false, isMutable = false, param0,
                                autoParamName, type, null,
                                Keywords.NONE, origin
                            )
                            listOf(LambdaVariable(type, field))
                        }
                        else -> {
                            // instead of throwing, we should probably just return some impossible type or error type...
                            throw IllegalStateException("Found lambda without parameters, but expected $size")
                        }
                    }
                }

                check(variables?.size == targetLambdaType.parameters.size)

                var bodyContext = bodyContext
                val newScopeType = targetLambdaType.selfType // todo replace generics inside this one?
                if (newScopeType != null) bodyContext = bodyContext.withSelfType(newScopeType)

                val newReturnType = if (targetLambdaType.returnType.containsGenerics()) {
                    // we need to inspect the contents
                    TypeResolution.resolveType(bodyContext, body)
                } else targetLambdaType.returnType // trust-me-bro
                val newParameters = variables!!.mapIndexed { index, param ->
                    val type = param.type ?: targetLambdaType.parameters[index].type
                    LambdaParameter(param.name, type)
                }
                return LambdaType(newScopeType, newParameters, newReturnType)
            }
            is ClassType -> {
                // is this ok??? OMG, looks like it is fine
                return targetLambdaType
            }
            null -> {
                // else 'it' is not defined
                if (variables == null) variables = emptyList()

                val returnType = TypeResolution.resolveType(bodyContext, body)
                return LambdaType(null, variables!!.map {
                    LambdaParameter(it.name, it.type!!)
                }, returnType)
            }
            else -> throw NotImplementedError("Extract LambdaType from $targetLambdaType")
        }
    }

    override fun resolveImpl(context: ResolutionContext): Expression {

        // todo get or create a custom anonymous class,
        //  - define field for outer scope variable
        //  - super type for invocation
        //  - define constructor
        //  - create instance
        // done
        //  - decide/generate name

        // todo generics are inherited from outer method & class...
        val scopeName = scope.generateName("lambda", origin)
        val classScope = scope.getOrPut(scopeName, ScopeType.INLINE_CLASS)
        val classConstructor = classScope.getOrCreatePrimConstructorScope()

        val superTypeI: ClassType = when (val superType = resolveType(context)) {
            is ClassType -> superType
            is LambdaType -> {
                val n = superType.parameters.size
                ClassType(
                    langScope.getOrPut("Function$n", ScopeType.INTERFACE),
                    superType.parameters.map { it.type } + superType.returnType)
            }
            else -> throw NotImplementedError("Convert $superType to class type")
        }
        classScope.superCalls.clear()
        classScope.superCalls.add(SuperCall(superTypeI, null, null))

        var selfMethodScope = scope
        while (true) {
            if (selfMethodScope.scopeType?.isMethodType() == true) break

            selfMethodScope = selfMethodScope.parentIfSameFile
                ?: throw IllegalStateException("Missing method scope for $this in ${resolveOrigin(origin)}")
        }

        val selfMethodType = selfMethodScope.typeWithArgs

        // todo if selfType != null, we need a second self-parameter
        val methodParameter = Parameter(0, "self", selfMethodType, classConstructor, origin)
        val methodField = classScope.addField(
            null, false, false, methodParameter, "self", selfMethodType,
            null, Keywords.SYNTHETIC, 0,
        )

        val constructorBody = ArrayList<Expression>()
        constructorBody.add(
            AssignmentExpression(
                DotExpression(
                    ThisExpression(classScope, scope, origin), emptyList(),
                    FieldExpression(methodField, scope, origin), scope, origin
                ),
                FieldExpression(methodParameter.getOrCreateField(null, Keywords.SYNTHETIC), scope, origin)
            )
        )
        classConstructor.selfAsConstructor = Constructor(
            listOf(methodParameter),
            classConstructor, null,
            ExpressionList(constructorBody, scope, origin), Keywords.SYNTHETIC, origin
        )

        val selfMethod = ThisExpression(selfMethodScope, scope, origin)
        /*return ConstructorExpression(
            classScope, emptyList(),
            listOf(NamedParameter(null, selfMethod)),
            null, scope, origin
        ).resolve(context)*/

        val method = ResolvedConstructor(
            ParameterList.emptyParameterList(),
            classConstructor.selfAsConstructor!!, context, scope
        )
        val params = listOf(selfMethod).map { it.resolve(context) }
        return ResolvedCallExpression(null, method, params, scope, origin)
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(body)
    }
}