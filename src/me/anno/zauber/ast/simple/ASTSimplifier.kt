package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.CheckEqualsOp
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.*
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaExpression
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.controlflow.SimpleYield
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.ThrowableType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.Specialization

object ASTSimplifier {

    val UnitInstance = SimpleField(UnitType, Ownership.COMPTIME, -1, UnitType.clazz)
    val booleanOwnership = Ownership.COMPTIME

    // todo inline functions
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
        block0: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean
    ): Pair<SimpleField, SimpleBlock>? {
        when (expr) {

            is ExpressionList -> {

                var result = UnitInstance
                val exprScope = expr.scope

                // declare fields -> needed?
                for (field in exprScope.fields) {
                    if (needsFieldByParameter(field.byParameter) &&
                        // field.originalScope == field.codeScope && // not moved
                        block0.instructions.none { it is SimpleDeclaration && it.name == field.name }
                    ) {
                        val type = field.resolveValueType(context)
                        block0.add(SimpleDeclaration(type, field.name, field.codeScope, field.origin))
                    }
                }

                var blockI = block0
                for (expr in expr.list) {
                    val tmp = simplifyImpl(context, expr, blockI, graph, needsValue) ?: return null
                    result = tmp.first
                    blockI = tmp.second
                }
                return result to blockI
            }

            is YieldExpression -> {
                val field = simplifyImpl(context, expr.value, block0, graph, true) ?: return null
                val block1 = field.second
                val continueBlock = graph.addBlock(expr.scope, expr.origin)
                block1.add(SimpleYield(field.first.use(), continueBlock, expr.scope, expr.origin))
                continueBlock.isEntryPoint = true
                return UnitInstance to continueBlock
            }

            is ReturnExpression -> {
                // this is the standard way to exit,
                //  but todo we also want yields for async functions and sequences
                val field = simplifyImpl(context, expr.value, block0, graph, true) ?: return null
                val block1 = field.second
                block1.add(SimpleReturn(field.first.use(), expr.scope, expr.origin))
                return null // nothing exists after
            }

            is ThrowExpression -> {
                // todo we need to check all handlers (go up the scope tree)...
                //   and finally, we need to exit
                val field = simplifyImpl(context, expr.value, block0, graph, true) ?: return null
                val block1 = field.second
                block1.add(SimpleThrow(field.first.use(), expr.scope, expr.origin))
                return null // nothing exists after
            }

            is TryCatchBlock -> {
                val scope = expr.tryBody.scope
                var handlerBlock = graph.addBlock(expr.scope, expr.origin)
                val throwableField = handlerBlock.field(ThrowableType)


                val finallyCondition = if (expr.finally != null) {
                    val field = graph.startBlock.field(BooleanType, booleanOwnership)
                    val trueField = block0.field(BooleanType, booleanOwnership)
                    block0.add(SimpleSpecialValue(trueField, SpecialValue.TRUE, expr.scope, expr.origin))
                    // todo what is self here???
                    // todo we must initialize the field to false at the very start...
                    block0.add(SimpleSetField(UnitInstance, expr.finally.flag, trueField, expr.scope, expr.origin))
                    field
                } else null

                val prevHandler = graph.catchHandlers.put(scope, SimpleCatchHandler(throwableField, handlerBlock))
                check(prevHandler == null)

                val body = simplifyImpl(context, expr.tryBody, block0, graph, needsValue)
                // todo the values of all catches must be joined if possible...

                // todo it would be good to calculate which stuff exactly can be thrown,
                //  and to therefore simplify this lots and lots

                var hasCaughtAll = false
                for (catch in expr.catches) {
                    // catches must be built into a long else-if-else-if chain
                    val type = catch.param.type
                    val catchesAll = type == ThrowableType

                    if (catchesAll) {
                        hasCaughtAll = true
                        val catchBody1 = simplifyImpl(context, catch.body, handlerBlock, graph, needsValue)
                        break
                    } else {
                        val condition = handlerBlock.field(BooleanType, booleanOwnership)
                        handlerBlock.add(SimpleInstanceOf(condition, throwableField, type, expr.scope, expr.origin))
                        handlerBlock.branchCondition = condition

                        val catchBlock0 = graph.addBlock(expr.scope, expr.origin)
                        val continueBlock = graph.addBlock(expr.scope, expr.origin)
                        handlerBlock.ifBranch = catchBlock0
                        handlerBlock.elseBranch = continueBlock

                        val catchBody1 = simplifyImpl(context, catch.body, catchBlock0, graph, needsValue)
                        handlerBlock = continueBlock
                    }
                }

                if (!hasCaughtAll) {
                    // todo the last handlerBlock case must be 'throw'
                    // todo except if one case handled all cases

                }

                if (finallyCondition != null) {
                    val finallyHandler = graph.addBlock(expr.scope, expr.origin)
                    simplifyImpl(context, expr.finally!!.body, finallyHandler, graph, false)
                    graph.finallyBlocks.add(SimpleFinallyBlock(finallyCondition, finallyHandler))
                }

                // todo simplify body
                // todo simplify handlers
                return body
            }

            is ResolvedCompareOp -> {
                val left = simplifyImpl(context, expr.left, block0, graph, true) ?: return null
                val block1 = left.second
                val right = simplifyImpl(context, expr.right, block1, graph, false) ?: return null
                val block2 = right.second
                val dst = block2.field(BooleanType, booleanOwnership)
                val instr = SimpleCompare(
                    dst, left.first.use(), right.first.use(), expr.type,
                    expr.callable, expr.scope, expr.origin
                )
                block2.add(instr)
                return dst to block2
            }

            is ResolvedCallExpression -> {
                val (base, block1) = simplifyImpl(context, expr.base, block0, graph, true) ?: return null
                var blockI = block1
                val valueParameters = expr.valueParameters.map { param ->
                    val (value, blockJ) = simplifyImpl(context, param, blockI, graph, false) ?: return null
                    blockI = blockJ
                    value
                }
                val method = expr.callable
                return simplifyCall(
                    blockI, base,
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
                val dst = block0.field(type)
                block0.add(SimpleSpecialValue(dst, expr.type, expr.scope, expr.origin))
                return dst to block0
            }

            is ThisExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = block0.field(type, expr.label)
                // currBlock.add(SimpleThis(dst, expr)) // <- self-explaining
                return dst to block0
            }

            is ResolvedGetFieldExpression -> {
                val field = expr.field.resolved
                val valueType = expr.run { resolvedType ?: resolveType(context) }
                val (self, block1) = simplifyImpl(context, expr.owner, block0, graph, true) ?: return null
                val dst = block1.field(valueType)
                block1.add(SimpleGetField(dst, self.use(), field, expr.scope, expr.origin))
                return dst to block1
            }

            is ResolvedSetFieldExpression -> {
                val field = expr.field.resolved
                val (self, block1) = simplifyImpl(context, expr.owner, block0, graph, true) ?: return null
                val (value, block2) = simplifyImpl(context, expr.value, block1, graph, true) ?: return null
                block2.add(SimpleSetField(self.use(), field, value.use(), expr.scope, expr.origin))
                return UnitInstance to block2
            }

            is NumberExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = block0.field(type)
                block0.add(SimpleNumber(dst, expr))
                return dst to block0
            }

            is StringExpression -> {
                val dst = block0.field(StringType, Ownership.COMPTIME)
                block0.add(SimpleString(dst, expr))
                return dst to block0
            }

            is IsInstanceOfExpr -> {
                val (src, block1) = simplifyImpl(context, expr.value, block0, graph, true) ?: return null
                val dst = block1.field(BooleanType, booleanOwnership)
                block1.add(SimpleInstanceOf(dst, src.use(), expr.type, expr.scope, expr.origin))
                return dst to block1
            }

            is IfElseBranch -> return simplifyBranch(context, expr, block0, graph, needsValue)
            is WhileLoop -> return simplifyWhile(context, expr, block0, graph)
            is DoWhileLoop -> return simplifyDoWhile(context, expr, block0, graph)

            is CheckEqualsOp -> {
                val (left, block1) = simplifyImpl(context, expr.left, block0, graph, true) ?: return null
                val (right, block2) = simplifyImpl(context, expr.right, block1, graph, true) ?: return null
                val dst = block2.field(BooleanType, booleanOwnership)
                val call = if (expr.byPointer) {
                    SimpleCheckIdentical(
                        dst, left.use(), right.use(),
                        expr.negated, expr.scope, expr.origin
                    )
                } else {
                    SimpleCheckEquals(
                        dst, left.use(), right.use(),
                        expr.negated, expr.scope, expr.origin
                    )
                }
                block2.add(call)
                return dst to block2
            }

            is LambdaExpression -> {
                // if target is an inline function, inline it here
                //  -> no, that should have been done previously
                // todo create an instance accordingly,
                //  constructor call + synthetic class

                TODO("Simplify lambda ${expr.javaClass.simpleName}: $expr")
            }
            else -> {
                if (!expr.isResolved()) {
                    val expr = expr.resolve(context)
                    return simplifyImpl(context, expr, block0, graph, needsValue)
                }
                TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
            }
        }
    }

    private fun simplifyWhile(
        context: ResolutionContext,
        expr: WhileLoop,
        block0: SimpleBlock,
        graph: SimpleGraph,
    ): Pair<SimpleField, SimpleBlock>? {

        val scope = expr.scope
        val origin = expr.origin

        val label = expr.label
        val afterBlock = graph.addBlock(scope, origin)
        val insideBlock0 = graph.addBlock(scope, origin)
        val beforeBlock0 = if (block0.isEmpty()) block0 else graph.addBlock(scope, origin)
        if (block0 !== insideBlock0) block0.nextBranch = beforeBlock0

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = beforeBlock0

        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = beforeBlock0
        }


        // add condition and jump to insideBlock
        val (condition, beforeBlock1) = simplifyImpl(context, expr.condition, beforeBlock0, graph, true) ?: return null
        beforeBlock1.branchCondition = condition
        beforeBlock1.ifBranch = insideBlock0
        beforeBlock1.elseBranch = afterBlock

        // add body to insideBlock
        val insideBlock1 = simplifyImpl(context, expr.body, insideBlock0, graph, false)
        if (insideBlock1 != null) {
            // continue, if possible
            insideBlock1.second.nextBranch = beforeBlock0
        }

        return UnitInstance to afterBlock // no return value is supported
    }

    private fun simplifyDoWhile(
        context: ResolutionContext,
        expr: DoWhileLoop,
        block0: SimpleBlock,
        graph: SimpleGraph,
    ): Pair<SimpleField, SimpleBlock>? {

        val scope = expr.scope
        val origin = expr.origin

        val label = expr.label
        val afterBlock = graph.addBlock(scope, origin)
        val insideBlock0 = if (block0.isEmpty()) block0 else graph.addBlock(scope, origin)
        val decideBlock = graph.addBlock(scope, origin)
        if (block0 !== insideBlock0) block0.nextBranch = insideBlock0

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = decideBlock

        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = decideBlock
        }

        val (_, insideBlock1) = simplifyImpl(context, expr.body, insideBlock0, graph, false) ?: return null
        insideBlock1.nextBranch = decideBlock

        // add condition and jump to insideBlock
        val (condition, decideBlock1) = simplifyImpl(context, expr.condition, decideBlock, graph, true) ?: return null
        decideBlock1.branchCondition = condition
        decideBlock1.ifBranch = insideBlock0
        decideBlock1.elseBranch = afterBlock

        return UnitInstance to afterBlock // no return value is supported
    }

    private fun simplifyBranch(
        context: ResolutionContext,
        expr: IfElseBranch,
        block0: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): Pair<SimpleField, SimpleBlock>? {

        val (condition, block1) = simplifyImpl(context, expr.condition, block0, graph, true)
            ?: return null

        val scope = expr.scope
        val origin = expr.origin
        val ifBlock = graph.addBlock(scope, origin)
        val elseBlock = graph.addBlock(scope, origin)

        block1.branchCondition = condition
        block1.ifBranch = ifBlock
        block1.elseBranch = elseBlock

        if (expr.elseBranch == null) {

            val (_, block2) = simplifyImpl(context, expr.ifBranch, ifBlock, graph, true) ?: return null
            return UnitInstance to block2

        } else {

            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, graph, true)
            val elseValue = simplifyImpl(context, expr.elseBranch, elseBlock, graph, true)
            if (ifValue == null && elseValue == null) {
                // finished / unreachable
                return null
            }

            if (ifValue == null || elseValue == null) {
                // just one returns -> continue on that branch/block
                return ifValue ?: elseValue!!
            }

            val mergedBlock = graph.addBlock(scope, origin)
            val mergedValue = if (needsValue) {
                val dst = mergedBlock.field(TypeResolution.resolveType(context, expr))
                val merge = SimpleMerge(
                    dst, ifBlock, ifValue.first.use(),
                    elseBlock, elseValue.first.use(), expr
                )
                mergedBlock.add(merge)
                dst
            } else UnitInstance

            mergedBlock.entryBlocks.add(ifValue.second)
            mergedBlock.entryBlocks.add(elseValue.second)
            return mergedValue to mergedBlock
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
        block0: SimpleBlock,
        graph: SimpleGraph,

        selfExpr: Expression,
        typeParameters: ParameterList,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): Pair<SimpleField, SimpleBlock>? {
        return when (val method = method0.resolved) {
            is Method -> {
                val self = simplifyImpl(context, selfExpr, block0, graph, true) ?: return null
                val (valueParametersI, block1) = reorderParameters(
                    context, block0, graph,
                    valueParameters, method0, scope, origin, method
                ) ?: return null
                // then execute it
                val dst = block1.field(method0.getTypeFromCall())
                for (param in valueParametersI) param.use()
                val specialization = collectSpecialization(method, typeParameters)
                block1.add(SimpleCall(dst, method, self.first.use(), specialization, valueParametersI, scope, origin))
                dst to block1
            }
            is Constructor -> {
                // base is a type
                val (params, block1) = reorderParameters(
                    context, block0, graph,
                    valueParameters, method0, scope, origin, method
                ) ?: return null
                createConstructorInvocation(
                    block1, method, params, method0,
                    selfIfInsideConstructor, scope, origin
                ) to block1
            }
            is Field -> {
                val fieldExpr = FieldExpression(method, scope, origin)
                val method0 = (method0 as ResolvedField).resolveCalledMethod(
                    typeParameters, resolveValueParameters(context, valueParameters)
                )
                simplifyCall(
                    context, block0, graph, fieldExpr,
                    ParameterList.emptyParameterList() /* the type is in the class, not the invocation */,
                    valueParameters, method0, null, scope, origin
                )
            }
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    private fun reorderParameters(
        context: ResolutionContext,
        block0: SimpleBlock,
        graph: SimpleGraph,

        valueParameters: List<NamedParameter>,
        method0: ResolvedMember<*>,

        scope: Scope,
        origin: Int,

        method: MethodLike
    ): Pair<List<SimpleField>, SimpleBlock>? {
        val params = reorderParameters(
            valueParameters,
            method.valueParameters,
            scope, origin
        )
        var blockI = block0
        val values = params.mapIndexed { index, parameter ->
            var targetType = method.valueParameters[index].type
            targetType = method0.ownerTypes.resolveGenerics(null, targetType)
            targetType = method0.callTypes.resolveGenerics(null, targetType)
            targetType = targetType.resolve().specialize()
            val contextI = context.withTargetType(targetType)
            val (value, blockJ) = simplifyImpl(contextI, parameter, blockI, graph, true)
                ?: return null
            blockI = blockJ
            value
        }
        return values to blockI
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
    ): Pair<SimpleField, SimpleBlock>? {
        for (param in valueParameters) param.use()
        return when (val method = method0.resolved) {
            is Method -> {
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                val specialization = collectSpecialization(method, typeParameters)
                currBlock.add(SimpleCall(dst, method, selfExpr.use(), specialization, valueParameters, scope, origin))
                dst to currBlock
            }
            is Constructor -> createConstructorInvocation(
                currBlock, method, valueParameters,
                method0, selfIfInsideConstructor, scope, origin
            ) to currBlock
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    private fun createConstructorInvocation(
        currBlock: SimpleBlock,

        method: Constructor,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): SimpleField {
        return if (selfIfInsideConstructor != null) {
            currBlock.add(
                SimpleSelfConstructor(
                    selfIfInsideConstructor,
                    method, valueParameters, scope, origin
                )
            )
            UnitInstance
        } else {
            val dst = currBlock.field(method0.getTypeFromCall())
            currBlock.add(SimpleAllocateInstance(dst, method.selfType, scope, origin))
            val unusedTmp = currBlock.field(UnitType)
            currBlock.add(
                SimpleCall( // todo use correct specialization; depends on outer class, if present, too
                    unusedTmp, "::new",
                    FullMap(method), method, dst.use(),
                    Specialization.noSpecialization, valueParameters, scope, origin
                )
            )
            dst
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