package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.*
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaExpression
import me.anno.zauber.ast.simple.controlflow.*
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.Specialization

object ASTSimplifier {

    val UnitInstance = SimpleField(UnitType, Ownership.COMPTIME, -1, UnitType.clazz)
    val booleanOwnership = Ownership.COMPTIME

    // todo inline functions
    // todo calculate labels for break/continue
    // todo calculate what errors a function throws,
    //  and handle all possibilities after each call

    fun simplify(context: ResolutionContext, expr: Expression): SimpleGraph {
        val expr = expr.resolve(context)
        val graph = SimpleGraph(expr.scope, expr.origin)
        simplifyImpl(context, expr, graph.startBlock, graph, false)
        return graph
    }

    fun needsFieldByParameter(parameter: Any?): Boolean {
        if (parameter == null) return true
        if (parameter !is Parameter) return false
        return parameter.isVal || parameter.isVar
    }

    private fun simplifyImpl(
        context: ResolutionContext,
        expr: Expression,
        currBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean
    ): SimpleField? {
        return when (expr) {

            is ExpressionList -> {
                var result = UnitInstance
                val exprScope = expr.scope
                for (field in exprScope.fields) {
                    if (needsFieldByParameter(field.byParameter) &&
                        // field.originalScope == field.codeScope && // not moved
                        currBlock.instructions.none { it is SimpleDeclaration && it.name == field.name }
                    ) {
                        val type = field.resolveValueType(context)
                        currBlock.add(SimpleDeclaration(type, field.name, field.codeScope, field.origin))
                    }
                }
                for (expr in expr.list) {
                    result = simplifyImpl(context, expr, currBlock, graph, needsValue)
                        ?: return null
                }
                result
            }

            is YieldExpression -> {
                // todo we need to split the flow...
                val field = simplifyImpl(context, expr.value, currBlock, graph, true)
                if (field != null) currBlock.add(SimpleYield(field.use(), expr.scope, expr.origin))
                UnitInstance
            }

            is ThrowExpression -> {
                // todo we need to check all handlers...
                //   and finally, we need to exit
                val field = simplifyImpl(context, expr.value, currBlock, graph, true)
                if (field != null) currBlock.add(SimpleThrow(field.use(), expr.scope, expr.origin))
                UnitInstance
            }

            is ReturnExpression -> {
                // this is the standard way to exit,
                //  but todo we also want yields for async functions and sequences
                val field = simplifyImpl(context, expr.value, currBlock, graph, true)
                if (field != null) currBlock.add(SimpleReturn(field.use(), expr.scope, expr.origin))
                // todo else write unreachable?
                UnitInstance
            }

            is ResolvedCompareOp -> {
                val left = simplifyImpl(context, expr.left, currBlock, graph, true) ?: return null
                val right = simplifyImpl(context, expr.right, currBlock, graph, false) ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                val instr = SimpleCompare(
                    dst, left.use(), right.use(), expr.type,
                    expr.callable, expr.scope, expr.origin
                )
                currBlock.add(instr)
                dst
            }

            is ResolvedCallExpression -> {
                val base = simplifyImpl(context, expr.base, currBlock, graph, true) ?: return null
                val valueParameters = expr.valueParameters.map { param ->
                    simplifyImpl(context, param, currBlock, graph, false) ?: return null
                }
                val method = expr.callable
                simplifyCall(
                    currBlock, base,
                    expr.callable.ownerTypes + expr.callable.callTypes,
                    valueParameters, method, null,
                    expr.scope, expr.origin
                )
            }

            is SpecialValueExpression -> {
                val type = when (expr.type) {
                    SpecialValue.NULL -> NullType
                    SpecialValue.TRUE, SpecialValue.FALSE -> BooleanType
                    SpecialValue.SUPER -> throw IllegalStateException("Cannot store super in a field")
                }
                val dst = currBlock.field(type)
                currBlock.add(SimpleSpecialValue(dst, expr))
                dst
            }

            is ThisExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = currBlock.field(type, expr.label)
                // currBlock.add(SimpleThis(dst, expr)) // <- self-explaining
                dst
            }

            is ResolvedGetFieldExpression -> {
                val field = expr.field.resolved
                val valueType = expr.run { resolvedType ?: resolveType(context) }
                val self: SimpleField? = if (expr.owner !is TypeExpression) {
                    simplifyImpl(context, expr.owner, currBlock, graph, true)
                        ?: return null
                } else null
                val dst = currBlock.field(valueType)
                currBlock.add(SimpleGetField(dst, self?.use(), field, expr.scope, expr.origin))
                dst
            }

            is ResolvedSetFieldExpression -> {
                val field = expr.field.resolved
                val self: SimpleField? = if (expr.owner !is TypeExpression) {
                    simplifyImpl(context, expr.owner, currBlock, graph, true)
                        ?: return null
                } else null
                val value = simplifyImpl(context, expr.value, currBlock, graph, true) ?: return null
                currBlock.add(SimpleSetField(self?.use(), field, value.use(), expr.scope, expr.origin))
                UnitInstance
            }

            is NumberExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = currBlock.field(type)
                currBlock.add(SimpleNumber(dst, expr))
                dst
            }

            is StringExpression -> {
                val dst = currBlock.field(StringType, Ownership.COMPTIME)
                currBlock.add(SimpleString(dst, expr))
                dst
            }

            is IsInstanceOfExpr -> {
                val src = simplifyImpl(context, expr.value, currBlock, graph, true)
                    ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                currBlock.add(SimpleInstanceOf(dst, src.use(), expr.type, expr.scope, expr.origin))
                dst
            }

            is IfElseBranch -> simplifyBranch(context, expr, currBlock, graph, needsValue)
            is WhileLoop -> simplifyWhile(context, expr, currBlock, graph, needsValue)
            is DoWhileLoop -> simplifyDoWhile(context, expr, currBlock, graph, needsValue)

            is CheckEqualsOp -> {
                val left = simplifyImpl(context, expr.left, currBlock, graph, true) ?: return null
                val right = simplifyImpl(context, expr.right, currBlock, graph, true) ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                val call = if (expr.byPointer) {
                    SimpleCheckIdentical(dst, left.use(), right.use(), expr.negated, expr.scope, expr.origin)
                } else {
                    SimpleCheckEquals(dst, left.use(), right.use(), expr.negated, expr.scope, expr.origin)
                }
                currBlock.add(call)
                dst
            }

            is LambdaExpression -> {
                // if target is an inline function, inline it here
                //  -> no, that should have been done previously
                // todo if not, create an instance accordingly

                TODO("Simplify lambda ${expr.javaClass.simpleName}: $expr")
            }
            else -> {
                if (!expr.isResolved()) {
                    val expr = expr.resolve(context)
                    return simplifyImpl(context, expr, currBlock, graph, needsValue)
                }
                TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
            }
        }
    }

    private fun simplifyWhile(
        context: ResolutionContext,
        expr: WhileLoop,
        currBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): SimpleField {

        val scope = expr.scope
        val origin = expr.origin

        val label = expr.label
        val afterBlock = graph.addBlock(scope, origin)
        val insideBlock = graph.addBlock(scope, origin)
        val beforeBlock = graph.addBlock(scope, origin)

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = beforeBlock

        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = beforeBlock
        }

        // add condition and jump to insideBlock
        val condition = simplifyImpl(context, expr.condition.not(), insideBlock, graph, needsValue)
            ?: throw IllegalStateException("Condition should return a boolean, not Nothing")
        insideBlock.add(SimpleGoto(condition.use(), insideBlock, afterBlock, true, scope, origin))

        // add body to insideBlock
        simplifyImpl(context, expr.body, insideBlock, graph, needsValue)

        val loop = SimpleLoop(insideBlock, scope, origin)
        currBlock.add(beforeBlock) // as a label
        currBlock.add(loop) // looping itself
        currBlock.add(afterBlock) // as a label & where to continue

        // todo we need to somehow split any further instructions
        //  to be placed into afterBlock, not currBlock
        return UnitInstance // no return value is supported
    }

    private fun simplifyDoWhile(
        context: ResolutionContext,
        expr: DoWhileLoop,
        currBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): SimpleField {

        val scope = expr.scope
        val origin = expr.origin

        val label = expr.label
        val afterBlock = graph.addBlock(scope, origin)
        val insideBlock = graph.addBlock(scope, origin)
        val continueBlock = graph.addBlock(scope, origin)

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = continueBlock
        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = continueBlock
        }

        // add body to insideBlock
        simplifyImpl(context, expr.body, insideBlock, graph, needsValue)

        insideBlock.add(continueBlock) // as a label

        // add condition and jump to insideBlock
        val condition = simplifyImpl(context, expr.condition.not(), continueBlock, graph, needsValue)
            ?: throw IllegalStateException("Condition should return a boolean, not Nothing")
        continueBlock.add(SimpleGoto(condition.use(), insideBlock, afterBlock, true, scope, origin))

        val loop = SimpleLoop(insideBlock, scope, origin)
        currBlock.add(loop) // looping itself
        currBlock.add(afterBlock) // as a label & where to continue

        // todo we need to somehow split any further instructions
        //  to be placed into afterBlock, not currBlock
        return UnitInstance // no return value is supported
    }

    private fun simplifyBranch(
        context: ResolutionContext,
        expr: IfElseBranch,
        currBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): SimpleField? {
        val dst = currBlock.field(TypeResolution.resolveType(context, expr))
        val condition = simplifyImpl(context, expr.condition, currBlock, graph, true)
            ?: throw IllegalStateException("Condition for if didn't return a field")
        // todo if not condition, create unreachable...
        if (expr.elseBranch == null) {
            val scope = expr.scope
            val origin = expr.origin
            val ifBlock = graph.addBlock(scope, origin)
            val elseBlock = graph.addBlock(scope, origin)
            simplifyImpl(context, expr.ifBranch, ifBlock, graph, true)
            currBlock.add(SimpleBranch(condition.use(), ifBlock, elseBlock, scope, origin))
            return UnitInstance
        } else {
            val scope = expr.scope
            val origin = expr.origin
            val ifBlock = graph.addBlock(scope, origin)
            val elseBlock = graph.addBlock(scope, origin)
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, graph, true)
            val elseValue = simplifyImpl(context, expr.elseBranch, elseBlock, graph, true)
            currBlock.add(SimpleBranch(condition.use(), ifBlock, elseBlock, scope, origin))
            return if (!needsValue) UnitInstance else {
                if (ifValue != null && elseValue != null) {
                    currBlock.add(SimpleMerge(dst, ifBlock, ifValue.use(), elseBlock, elseValue.use(), expr))
                    dst
                } else (ifValue ?: elseValue)?.use()
            }
        }
    }

    fun collectSpecialization(method: Method, typeParameters: ParameterList): Specialization {
        // todo implement this...
        //  we must collect the following:
        //  method-type-parameters,
        //  outer class type-parameters
        return Specialization(typeParameters)
    }

    private fun simplifyCall(
        context: ResolutionContext,
        currBlock: SimpleBlock,
        graph: SimpleGraph,

        selfExpr: Expression,
        typeParameters: ParameterList,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): SimpleField? {
        when (val method = method0.resolved) {
            is Method -> {
                val self = simplifyImpl(context, selfExpr, currBlock, graph, true) ?: return null
                val valueParametersI = reorderParameters(
                    context, currBlock, graph,
                    valueParameters, method0, scope, origin, method
                ) ?: return null
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                for (param in valueParametersI) param.use()
                val specialization = collectSpecialization(method, typeParameters)
                currBlock.add(SimpleCall(dst, method, self.use(), specialization, valueParametersI, scope, origin))
                return dst
            }
            is Constructor -> {
                // base is a type
                val params = reorderParameters(
                    context, currBlock, graph,
                    valueParameters, method0, scope, origin, method
                ) ?: return null
                // then execute it
                for (param in params) param.use()
                if (selfIfInsideConstructor != null) {
                    currBlock.add(
                        SimpleSelfConstructor(
                            selfIfInsideConstructor,
                            method, params, scope, origin
                        )
                    )
                    return UnitInstance
                } else {
                    val dst = currBlock.field(method0.getTypeFromCall())
                    currBlock.add(SimpleConstructor(dst, method, params, scope, origin))
                    return dst
                }
            }
            is Field -> {
                val fieldExpr = FieldExpression(method, scope, origin)
                val method0 = (method0 as ResolvedField).resolveCalledMethod(
                    typeParameters, resolveValueParameters(context, valueParameters)
                )
                return simplifyCall(
                    context,
                    currBlock,
                    graph,
                    fieldExpr,
                    ParameterList.emptyParameterList() /* the type is in the class, not the invocation */,
                    valueParameters,
                    method0,
                    null,
                    scope,
                    origin
                )
            }
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    private fun reorderParameters(
        context: ResolutionContext,
        currBlock: SimpleBlock,
        graph: SimpleGraph,

        valueParameters: List<NamedParameter>,
        method0: ResolvedMember<*>,

        scope: Scope,
        origin: Int,

        method: MethodLike
    ): List<SimpleField>? {
        return reorderParameters(
            valueParameters,
            method.valueParameters,
            scope, origin
        ).mapIndexed { index, parameter ->
            var targetType = method.valueParameters[index].type
            targetType = method0.ownerTypes.resolveGenerics(null, targetType)
            targetType = method0.callTypes.resolveGenerics(null, targetType)
            targetType = targetType.resolve().specialize()
            simplifyImpl(context.withTargetType(targetType), parameter, currBlock, graph, true)
                ?: return null
        }
    }

    private fun simplifyCall(
        currBlock: SimpleBlock,

        selfExpr: SimpleField,
        typeParameters: ParameterList,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): SimpleField {
        for (param in valueParameters) param.use()
        when (val method = method0.resolved) {
            is Method -> {
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                val specialization = collectSpecialization(method, typeParameters)
                currBlock.add(SimpleCall(dst, method, selfExpr.use(), specialization, valueParameters, scope, origin))
                return dst
            }
            is Constructor -> {
                // base is a type
                // then execute it
                if (selfIfInsideConstructor != null) {
                    currBlock.add(
                        SimpleSelfConstructor(
                            selfIfInsideConstructor,
                            method, valueParameters, scope, origin
                        )
                    )
                    return UnitInstance
                } else {
                    val dst = currBlock.field(method0.getTypeFromCall())
                    currBlock.add(SimpleConstructor(dst, method, valueParameters, scope, origin))
                    return dst
                }
            }
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    fun reorderParameters(
        src: List<NamedParameter>, dst: List<Parameter>,
        scope: Scope, origin: Int
    ): List<Expression> {
        return resolveNamedParameters(dst, src, scope, origin)
            ?: throw IllegalStateException("Failed to fill in call parameters")
    }

}