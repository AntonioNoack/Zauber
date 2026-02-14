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
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.controlflow.SimpleYield
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.interpreting.ZClass.Companion.needsBackingFieldImpl
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

object ASTSimplifier {

    private val LOGGER = LogManager.getLogger(ASTSimplifier::class)

    val UnitInstance = SimpleField(UnitType, Ownership.COMPTIME, -1, UnitType.clazz)
    val voidResult = FlowResult(null, null, null)
    val booleanOwnership = Ownership.COMPTIME

    private val cache = HashMap<MethodSpecialization, SimpleGraph>()

    // todo inline functions
    // todo calculate what errors a function throws,
    //  and handle all possibilities after each call

    fun simplify(method: MethodSpecialization): SimpleGraph {
        return cache.getOrPut(method) {
            val context = ResolutionContext(null, method.specialization, true, null)
            val expr = method.method.getSpecializedBody(method.specialization)!!
            LOGGER.info("Simplifying $expr")
            val graph = SimpleGraph(method.method)
            val flow0 = FlowResult(Flow(UnitInstance, graph.startBlock), null, null)
            val flow1 = simplifyImpl(context, expr, graph.startBlock, flow0, graph, false)
            val flow1r = flow1.returned
            flow1r?.block?.add(SimpleReturn(flow1r.value.use(), expr.scope, expr.origin))
            val flow1t = flow1.thrown
            flow1t?.block?.add(SimpleThrow(flow1t.value.use(), expr.scope, expr.origin))
            LOGGER.info("Simplified $flow1 to $graph for $method")
            graph
        }
    }

    fun needsFieldByParameter(parameter: Any?): Boolean {
        if (parameter == null) return true
        if (parameter !is Parameter) return false
        return parameter.isVal || parameter.isVar
    }

    private fun simplifyImpl(
        context: ResolutionContext,
        expr: Expression,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {
        val block0 = flow0.value ?: return flow0
        return simplifyImpl(context, expr, block0.block, flow0, graph, needsValue)
    }

    private fun simplifyImpl(
        context: ResolutionContext,
        expr: Expression,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {
        when (expr) {
            is ExpressionList -> return simplifyList(context, expr, block0, flow0, graph, needsValue)
            is YieldExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, graph, true)
                val field1v = field.value ?: return field
                val continueBlock = graph.addNode()
                field1v.block.add(SimpleYield(field1v.value.use(), continueBlock, expr.scope, expr.origin))
                continueBlock.isEntryPoint = true
                return field.withValue(UnitInstance, continueBlock)
            }

            is ReturnExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, graph, true)
                val field1v = field.value ?: return field
                return field.joinReturnNoValue(field1v.value.use(), field1v.block)
            }

            is ThrowExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, graph, true)
                val field1v = field.value ?: return field
                return field.joinThrownNoValue(field1v.value.use(), field1v.block)
            }

            is ResolvedCompareOp -> return simplifyCompareOp(context, expr, block0, flow0, graph)
            is ResolvedCallExpression -> return simplifyCall(context, expr, block0, flow0, graph)

            is SpecialValueExpression -> {
                val type = when (expr.type) {
                    SpecialValue.NULL -> NullType
                    SpecialValue.TRUE, SpecialValue.FALSE -> BooleanType
                    SpecialValue.SUPER -> throw IllegalStateException("Cannot store super in a field")
                }
                val dst = block0.field(type)
                block0.add(SimpleSpecialValue(dst, expr.type, expr.scope, expr.origin))
                return flow0.withValue(dst, block0)
            }

            is ThisExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = block0.field(type, expr.label)
                // currBlock.add(SimpleThis(dst, expr)) // <- self-explaining
                return flow0.withValue(dst, block0)
            }

            is ResolvedGetFieldExpression -> return simplifyGetField(context, expr, block0, flow0, graph)
            is ResolvedSetFieldExpression -> return simplifySetField(context, expr, block0, flow0, graph)

            is NumberExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = block0.field(type)
                block0.add(SimpleNumber(dst, expr))
                return flow0.withValue(dst, block0)
            }

            is StringExpression -> {
                val dst = block0.field(StringType, Ownership.COMPTIME)
                block0.add(SimpleString(dst, expr))
                return flow0.withValue(dst, block0)
            }

            is IsInstanceOfExpr -> {
                val block1 = simplifyImpl(context, expr.value, block0, flow0, graph, true)
                val block1v = block1.value ?: return block1
                val dst = block1v.block.field(BooleanType, booleanOwnership)
                block1v.block.add(SimpleInstanceOf(dst, block1v.value.use(), expr.type, expr.scope, expr.origin))
                return block1.withValue(dst, block1v.block)
            }

            is IfElseBranch -> return simplifyBranch(context, expr, block0, flow0, graph, needsValue)
            is WhileLoop -> return simplifyWhile(context, expr, block0, flow0, graph)
            is DoWhileLoop -> return simplifyDoWhile(context, expr, block0, flow0, graph)
            is TryCatchBlock -> return simplifyTryCatch(context, expr, block0, flow0, graph, needsValue)
            is CheckEqualsOp -> return simplifyCheckEqualsOp(context, expr, block0, flow0, graph)

            else -> {
                if (!expr.isResolved()) throw IllegalStateException("${expr.javaClass.simpleName} was not resolved")
                throw NotImplementedError("Simplify value ${expr.javaClass.simpleName}: $expr")
            }
        }
    }

    private fun simplifyList(
        context: ResolutionContext,
        expr: ExpressionList,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {
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

        var blockI = flow0
        for (expr in expr.list) {
            val blockIv = blockI.value ?: return blockI
            blockI = simplifyImpl(context, expr, blockIv.block, blockI, graph, needsValue)
        }
        return blockI
    }

    private fun simplifyCall(
        context: ResolutionContext,
        expr: ResolvedCallExpression,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {

        // (base, block1)
        val block1 = simplifyImpl(context, expr.self, block0, flow0, graph, true)
        val base = block1.value ?: return block1

        // println("Simplified self to ${expr.self} (${expr.self.javaClass.simpleName})")
        var blockI = block1
        val valueParameters = expr.valueParameters.map { param ->
            blockI = simplifyImpl(context, param, blockI.value!!.block, blockI, graph, false)
            blockI.value?.value ?: return blockI
        }

        val method = expr.callable
        return simplifyCall(
            blockI.value!!.block, blockI, graph, base.value.use(),
            valueParameters, method, null,
            expr.scope, expr.origin
        )
    }

    private fun simplifyGetField(
        context: ResolutionContext,
        expr: ResolvedGetFieldExpression,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {
        val field = expr.field.resolved
        val valueType = expr.run { resolvedType ?: resolveReturnType(context) }

        val block1 = simplifyImpl(context, expr.self, block0, flow0, graph, true)
        val block1v = block1.value ?: return block1
        val self = block1v.value

        val dst = block1v.block.field(valueType)
        // todo also, if the field is marked as open (and has children), or if the class is an interface
        val useGetter = !expr.field.isBackingField && (field.hasCustomGetter || !field.needsBackingFieldImpl())
        if (useGetter) {
            // todo we may need to resolve owner types, don't we?
            // todo is context correct?
            val method0 = ResolvedMethod(
                ParameterList.emptyParameterList(), field.getter!!,
                ParameterList.emptyParameterList(),
                context, expr.scope, MatchScore(0)
            )
            return simplifyCall(
                block1v.block, block1, graph, self,
                emptyList(), method0,
                null, expr.scope, expr.origin
            )
        } else {
            block1v.block.add(SimpleGetField(dst, self.use(), field, expr.scope, expr.origin))
            return block1.withValue(dst, block1v.block)
        }
    }

    private fun simplifySetField(
        context: ResolutionContext,
        expr: ResolvedSetFieldExpression,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {

        val field = expr.field.resolved

        val block1 = simplifyImpl(context, expr.self, block0, flow0, graph, true)
        val block1v = block1.value ?: return block1
        val self = block1v.value

        val block2 = simplifyImpl(context, expr.value, block1, graph, true)
        val block2v = block2.value ?: return block2
        val value = block2v.value

        // todo also, if the field is marked as open (and has children), or if the class is an interface
        val useSetter = !expr.field.isBackingField && (field.hasCustomSetter || !field.needsBackingFieldImpl())
        if (useSetter) {
            // todo we may need to resolve owner types, don't we?
            // todo is context correct?
            val method0 = ResolvedMethod(
                ParameterList.emptyParameterList(), field.setter!!,
                ParameterList.emptyParameterList(),
                context, expr.scope, MatchScore(0)
            )
            return simplifyCall(
                block2v.block, block2, graph, self,
                listOf(value), method0,
                null, expr.scope, expr.origin
            )
        } else {
            block2v.block.add(SimpleSetField(self.use(), field, value.use(), expr.scope, expr.origin))
            return block2.withValue(UnitInstance, block2v.block)
        }
    }

    private fun simplifyCompareOp(
        context: ResolutionContext,
        expr: ResolvedCompareOp,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {
        val block1 = simplifyImpl(context, expr.left, block0, flow0, graph, true)
        val block1v = block1.value ?: return block1
        val left = block1v.value

        val block2 = simplifyImpl(context, expr.right, block1, graph, false)
        val block2v = block2.value ?: return block2
        val right = block2v.value

        val tmp = block2v.block.field(IntType)
        val method = expr.callable.resolved
        val specialization = expr.callable.specialization
        val call = SimpleCall(
            tmp, method, left.use(),
            specialization, listOf(right.use()),
            expr.scope, expr.origin
        )

        val block3 = handleThrown(
            block2v.block, block2, graph,
            tmp, call, method.getThrownType(specialization)
        )
        val block3v = block3.value ?: return block3

        val dst = block3v.block.field(BooleanType, booleanOwnership)
        val instr = SimpleCompare(
            dst, left.use(), right.use(), expr.type,
            tmp.use(), expr.scope, expr.origin
        )
        block3v.block.add(instr)
        return block3.withValue(dst)
    }

    private fun simplifyCheckEqualsOp(
        context: ResolutionContext,
        expr: CheckEqualsOp,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {
        val block1 = simplifyImpl(context, expr.left, block0, flow0, graph, true)
        val block1v = block1.value ?: return block1

        val block2 = simplifyImpl(context, expr.right, block1, graph, true)
        val block2v = block2.value ?: return block2

        val dst = block2v.block.field(BooleanType, booleanOwnership)
        val left = block1v.value
        val right = block2v.value
        val call = if (expr.byPointer) {
            SimpleCheckIdentical(
                dst, left.use(), right.use(),
                expr.negated, expr.scope, expr.origin
            )
        } else {
            // a == null ? b == null : b != null ? a.equals(b) : false
            // todo inline this call if both sides are non-nullable??
            /*if(left.type.mayBeNull() || right.type.mayBeNull()) {

            }*/
            SimpleCheckEquals(
                dst, left.use(), right.use(),
                expr.negated, expr.resolved!!.callable as ResolvedMethod,
                expr.scope, expr.origin
            )
            // todo handle error
        }
        block2v.block.add(call)
        return flow0
            .joinError(block1)
            .joinError(block2)
            .withValue(dst, block2v.block)
    }

    private fun simplifyTryCatch(
        context: ResolutionContext,
        expr: TryCatchBlock,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {
        check(block0 == flow0.value?.block)
        val body = simplifyImpl(context, expr.tryBody, flow0, graph, false)
        val allCaught = simplifyCatches(context, expr, body, graph, needsValue)
        return simplifyFinally(context, expr, allCaught, graph)
    }

    private fun simplifyCatches(
        context: ResolutionContext,
        expr: TryCatchBlock,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {
        var blockI = flow0
        for (catch in expr.catches) {
            blockI = simplifyCatch(context, expr, catch, blockI, graph, needsValue)
        }
        return blockI
    }

    private fun simplifyCatch(
        context: ResolutionContext,
        expr: TryCatchBlock,
        catch: Catch,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean
    ): FlowResult {

        val block0 = flow0.thrown ?: return flow0
        val block0b = block0.block

        val thrownType = andTypes(catch.param.type, block0.value.type)
        val instanceOfField = block0b.field(BooleanType, booleanOwnership)
        block0b.add(SimpleInstanceOf(instanceOfField, block0.value, thrownType, expr.scope, expr.origin))

        val ifBlock = graph.addNode()
        val elseBlock = graph.addNode()
        block0b.branchCondition = instanceOfField.use()
        block0b.ifBranch = ifBlock
        block0b.elseBranch = elseBlock

        val ifFlow = FlowResult(Flow(UnitInstance, ifBlock), null, null)

        // val notThrownType = andTypes(catch.param.type.not(), block0.value.type)
        val elseFlow = flow0.withThrown(block0.value, elseBlock)

        val methodType = catch.param.scope.typeWithoutArgs
        val selfField = ifBlock.field(methodType, catch.param.scope)
        val thrownField = catch.param.getOrCreateField(null, Keywords.NONE)
        ifBlock.add(SimpleSetField(selfField, thrownField, block0.value, expr.scope, expr.origin))
        val ifFlow1 = simplifyImpl(context, catch.body, ifBlock, ifFlow, graph, needsValue)

        return ifFlow1.joinWith(elseFlow)
    }

    private fun simplifyFinally(
        context: ResolutionContext,
        expr: TryCatchBlock,
        flow0: FlowResult,
        graph: SimpleGraph
    ): FlowResult {

        val finally = expr.finally ?: return flow0

        // apply finally-block to normal execution flow
        val value = if (flow0.value != null) {
            simplifyImpl(context, finally, flow0, graph, false)
                .withValue(flow0.value.value)
        } else voidResult

        // apply finally-block to returned values
        val returned = if (flow0.returned != null) {
            val returnFlow = flow0.returnToValue()
            simplifyImpl(context, finally, returnFlow, graph, false)
                .valueToReturn(flow0.returned)
        } else null

        // apply finally-block to thrown values
        val thrown = if (flow0.thrown != null) {
            val throwFlow = flow0.thrownToValue()
            simplifyImpl(context, finally, throwFlow, graph, false)
                .valueToThrown(flow0.thrown)
        } else null

        return value
            .joinError(returned)
            .joinError(thrown)
    }

    private fun simplifyWhile(
        context: ResolutionContext,
        expr: WhileLoop,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
    ): FlowResult {

        val label = expr.label
        val conditionBlock = block0.nextOrSelfIfEmpty(graph)
        val bodyBlock = graph.addNode()
        val afterBlock = graph.addNode()

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = conditionBlock

        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = conditionBlock
        }

        val beforeFlow0 = flow0.withValue(UnitInstance, conditionBlock)
        // add condition and jump to insideBlock
        // (condition, beforeBlock1)
        val beforeBlock1 = simplifyImpl(context, expr.condition, conditionBlock, beforeFlow0, graph, true)
        val beforeBlock1v = beforeBlock1.value ?: return beforeBlock1
        val condition = beforeBlock1v.value
        beforeBlock1v.block.branchCondition = condition.use()
        beforeBlock1v.block.ifBranch = bodyBlock
        beforeBlock1v.block.elseBranch = afterBlock

        // add body to insideBlock
        val insideFlow0 = beforeBlock1.withValue(UnitInstance, bodyBlock)
        val insideBlock1 = simplifyImpl(context, expr.body, bodyBlock, insideFlow0, graph, false)
        // continue, if possible
        insideBlock1.value?.block?.nextBranch = conditionBlock

        return beforeBlock1.withValue(UnitInstance, afterBlock)
    }

    private fun simplifyDoWhile(
        context: ResolutionContext,
        expr: DoWhileLoop,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
    ): FlowResult {

        val label = expr.label
        val bodyBlock = block0.nextOrSelfIfEmpty(graph)
        val conditionBlock = graph.addNode()
        val afterBlock = graph.addNode()

        graph.breakLabels[null] = afterBlock
        graph.continueLabels[null] = conditionBlock

        if (label != null) {
            graph.breakLabels[label] = afterBlock
            graph.continueLabels[label] = conditionBlock
        }

        val insideFlow0 = flow0.withValue(UnitInstance, bodyBlock)
        val insideBlock1 = simplifyImpl(context, expr.body, bodyBlock, insideFlow0, graph, false)
        var flow1 = flow0.joinError(insideBlock1)
        if (insideBlock1.value != null) {
            insideBlock1.value.block.nextBranch = conditionBlock

            // add condition and jump to insideBlock
            val flow1d = flow1.withValue(UnitInstance, conditionBlock)
            val decideBlock1i = simplifyImpl(context, expr.condition, conditionBlock, flow1d, graph, true)
            if (decideBlock1i.value != null) {
                val decideBlock1 = decideBlock1i.value.block
                decideBlock1.branchCondition = decideBlock1i.value.value.use()
                decideBlock1.ifBranch = bodyBlock
                decideBlock1.elseBranch = afterBlock
            }
            flow1 = flow1.joinError(decideBlock1i)
        }

        // Unit, because no return value is supported
        return flow1.withValue(UnitInstance, afterBlock)
    }

    private fun simplifyBranch(
        context: ResolutionContext,
        expr: IfElseBranch,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): FlowResult {

        val block1 = simplifyImpl(context, expr.condition, block0, flow0, graph, true)
        val block1v = block1.value ?: return block1
        val condition = block1v.value

        val ifBlock = graph.addNode()
        val elseBlock = graph.addNode()

        block1v.block.branchCondition = condition.use()
        block1v.block.ifBranch = ifBlock
        block1v.block.elseBranch = elseBlock

        val ifFlow = block1.withValue(UnitInstance, ifBlock)
        val elseFlow = block1.withValue(UnitInstance, elseBlock)

        if (expr.elseBranch == null) {
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, ifFlow, graph, needsValue)
            ifValue.value?.block?.nextBranch = elseBlock

            return elseFlow.joinError(ifValue)
                .withValue(UnitInstance, elseBlock)
        } else {
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, ifFlow, graph, needsValue)
            val elseValue = simplifyImpl(context, expr.elseBranch, elseBlock, elseFlow, graph, needsValue)
            return ifValue.joinWith(elseValue)
        }
    }

    fun collectSpecialization(method: MethodLike, typeParameters: ParameterList): Specialization {
        // todo implement this...
        //  we must collect the following:
        //  method-type-parameters,
        //  outer class type-parameters
        return Specialization(typeParameters)
    }

    private fun simplifyCall(
        context: ResolutionContext,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        selfExpr: Expression,
        typeParameters: ParameterList,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope, origin: Int
    ): FlowResult {
        return when (val method = method0.resolved) {
            is Method -> simplifyMethodCall(
                context, block0, flow0, graph, selfExpr, valueParameters,
                method0, method, scope, origin
            )
            is Constructor -> simplifyConstructorCall(
                context, block0, flow0, graph, valueParameters,
                method0, method, selfIfInsideConstructor, scope, origin
            )
            is Field -> simplifyFieldCall(
                context, block0, flow0, graph, selfExpr, typeParameters, valueParameters,
                method0, method, scope, origin
            )
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    private fun simplifyMethodCall(
        context: ResolutionContext,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        selfExpr: Expression,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
        method: Method,

        scope: Scope, origin: Int
    ): FlowResult {
        val self = simplifyImpl(context, selfExpr, block0, flow0, graph, true)
        val self0 = self.value ?: return self

        val (valueParametersI, block1) = reorderParameters(
            context, block0, self, graph,
            valueParameters, method0, scope, origin, method
        )

        val block1v = block1.value
        if (valueParametersI == null || block1v == null) return block1

        // then execute it
        val dst = block1v.block.field(method0.getTypeFromCall())
        for (param in valueParametersI) param.use()
        val specialization = method0.specialization
        val call = SimpleCall(dst, method, self0.value.use(), specialization, valueParametersI, scope, origin)
        return handleThrown(block1v.block, block1, graph, dst, call, method.getThrownType(specialization))
    }

    private fun simplifyConstructorCall(
        context: ResolutionContext,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>, method: Constructor,
        selfIfInsideConstructor: Boolean?,

        scope: Scope, origin: Int
    ): FlowResult {
        // base is a type
        val (params, block1) = reorderParameters(
            context, block0, flow0, graph,
            valueParameters, method0, scope, origin, method
        )

        val block1v = block1.value
        if (params == null || block1v == null) return block1

        return createConstructorInvocation(
            block1v.block, block1, graph,
            method, params, method0,
            selfIfInsideConstructor, scope, origin
        )
    }

    private fun simplifyFieldCall(
        context: ResolutionContext,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        selfExpr: Expression,
        typeParameters: ParameterList,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>, method: Field,
        scope: Scope, origin: Int
    ): FlowResult {
        // todo why is self not used???
        val fieldExpr = FieldExpression(method, scope, origin)
        val method0 = (method0 as ResolvedField).resolveCalledMethod(
            typeParameters, resolveValueParameters(context, valueParameters)
        )
        return simplifyCall(
            context, block0, flow0, graph, fieldExpr,
            ParameterList.emptyParameterList() /* the type is in the class, not the invocation */,
            valueParameters, method0, null, scope, origin
        )
    }

    private fun reorderParameters(
        context: ResolutionContext,
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        valueParameters: List<NamedParameter>,
        method0: ResolvedMember<*>,

        scope: Scope,
        origin: Int,

        method: MethodLike
    ): Pair<List<SimpleField>?, FlowResult> {
        val params = reorderParameters(
            valueParameters,
            method.valueParameters,
            scope, origin
        )
        check(block0 == flow0.value?.block)
        var blockI = flow0
        val values = params.mapIndexed { index, parameter ->
            var targetType = method.valueParameters[index].type
            targetType = method0.selfTypeParameters.resolveGenerics(null, targetType)
            targetType = method0.callTypeParameters.resolveGenerics(null, targetType)
            targetType = targetType.resolve().specialize()
            val contextI = context.withTargetType(targetType)
            // (value, blockJ)
            blockI = simplifyImpl(contextI, parameter, blockI.value!!.block, blockI, graph, true)
            blockI.value?.value ?: return null to blockI
        }
        return values to blockI
    }

    private fun simplifyCall(
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        selfExpr: SimpleField,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): FlowResult {
        for (param in valueParameters) param.use()
        return when (val method = method0.resolved) {
            is Method -> {
                // then execute it
                val dst = block0.field(method0.getTypeFromCall())
                val specialization = method0.specialization
                val call = SimpleCall(dst, method, selfExpr.use(), specialization, valueParameters, scope, origin)
                handleThrown(block0, flow0, graph, dst, call, method.getThrownType(specialization))
            }
            is Constructor -> createConstructorInvocation(
                block0, flow0, graph, method, valueParameters,
                method0, selfIfInsideConstructor, scope, origin
            )
            else -> throw NotImplementedError("Simplify call $method, ${resolveOrigin(origin)}")
        }
    }

    private fun createConstructorInvocation(
        block0: SimpleNode,
        flow0: FlowResult,
        graph: SimpleGraph,

        method: Constructor,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): FlowResult {
        return if (selfIfInsideConstructor != null) {
            val constructor = SimpleSelfConstructor(
                UnitInstance,
                selfIfInsideConstructor,
                method, method0.specialization, valueParameters, scope, origin
            )
            handleThrown(block0, flow0, graph, UnitInstance, constructor, method.getThrownType(method0.specialization))
        } else {
            val dst = block0.field(method0.getTypeFromCall())
            var selfType = method.selfType
            if (selfType.typeParameters == null) {
                selfType = ClassType(selfType.clazz, method0.selfTypeParameters)
            }
            // todo allocation could fail, too...
            block0.add(SimpleAllocateInstance(dst, selfType, scope, origin))
            val unusedTmp = block0.field(UnitType)
            val specialization = method0.specialization
            val call = SimpleCall(unusedTmp, method, dst.use(), specialization, valueParameters, scope, origin)
            println("finding throw type for $method0")
            handleThrown(block0, flow0, graph, dst, call, method.getThrownType(specialization))
        }
    }

    fun handleThrown(
        block0: SimpleNode, flow0: FlowResult, graph: SimpleGraph,
        result: SimpleField, callable: SimpleCallable, thrownType: Type
    ): FlowResult {

        block0.add(callable)

        val flow1 = flow0.withValue(result, block0)
        if (thrownType == NothingType) return flow1

        val throwBlock = graph.addNode()
        val throwField = throwBlock.field(thrownType)
        val throwFlow = Flow(throwField, throwBlock)
        callable.onThrown = throwFlow

        val flow2 = flow1.joinError(throwFlow)
            .withValue(result, block0)
        println("joining: $flow1 x $throwFlow = $flow2")
        return flow2
    }

    fun reorderResolveParameters(
        context: ResolutionContext,
        src: List<NamedParameter>, targetParams: List<Parameter>,
        scope: Scope, origin: Int
    ): List<Expression> {
        return reorderParameters(src, targetParams, scope, origin).mapIndexed { index, param ->
            param.resolve(context.withTargetType(targetParams[index].type))
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