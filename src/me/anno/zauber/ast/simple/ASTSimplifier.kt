package me.anno.zauber.ast.simple

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.SpecialFieldNames.OUTER_FIELD_NAME
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.FieldGetterSetter.finishGetter
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.resolved.*
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.controlflow.SimpleYield
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.ast.simple.expression.SimpleInstanceOf.Companion.createSimpleInstanceOf
import me.anno.zauber.interpreting.ZClass.Companion.needsToBeStored
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.members.MatchScore
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.arithmetic.NullType

object ASTSimplifier {

    private val LOGGER = LogManager.getLogger(ASTSimplifier::class)

    val voidResult = FlowResult(null, null, null)

    private val cache by threadLocal { HashMap</*Method*/Specialization, SimpleGraph>() }

    fun unitInstance(graph: SimpleGraph, scope: Scope, origin: Long): SimpleField {
        var field = graph.unitField
        if (field != null) return field
        val block = graph.startBlock
        val type = Types.Unit
        field = block.field(type)
        block.add0(SimpleGetObject(field, type.clazz, scope, origin))
        graph.unitField = field
        return field.use()
    }

    fun unitInstance(graph: SimpleGraph, expr: Expression): SimpleField {
        return unitInstance(graph, expr.scope, expr.origin)
    }

    // todo inline functions
    // todo calculate what errors a function throws,
    //  and handle all possibilities after each call

    fun simplify(method: Specialization): SimpleGraph {
        check(method.isMethodLike())
        return cache.getOrPut(method) {
            val context = ResolutionContext(null, method, true, null)
            val expr = method.method.getSpecializedBody(method)
                ?: throw IllegalStateException("Specialized body is null? For $method")
            LOGGER.info("Simplifying $expr")
            val graph = SimpleGraph(method.method)
            val flow0 = FlowResult(Flow(unitInstance(graph, expr), graph.startBlock), null, null)
            val flow1 = simplifyImpl(context, expr, graph.startBlock, flow0, false)
            graph.endFlow = flow1
            finishFlows(flow1, method, expr)

            LOGGER.info("Simplified $method:\n  $flow1\n  to $graph")
            graph
        }
    }

    private fun finishFlows(flow1: FlowResult, method: Specialization, expr: Expression) {
        val flow1v = flow1.value
        if (flow1v != null) {
            // missing return -> we do it ourselves
            // validate method returns Unit
            check(method.method.returnType == Types.Unit) {
                "Expected $method to return Unit, because it's missing an explicit return"
            }
            // push object & return it
            val objectType = Types.Unit
            val instance = flow1v.block.field(objectType)
            flow1v.block.add(SimpleGetObject(instance, objectType.clazz, expr.scope, expr.origin))
            flow1v.block.add(SimpleReturn(instance.use(), expr.scope, expr.origin))
        }
        val flow1r = flow1.returned
        flow1r?.block?.add(SimpleReturn(flow1r.value.use(), expr.scope, expr.origin))
        val flow1t = flow1.thrown
        flow1t?.block?.add(SimpleThrow(flow1t.value.use(), expr.scope, expr.origin))
    }

    fun needsFieldByParameter(parameter: Any?): Boolean {
        if (parameter == null) return true
        if (parameter !is Parameter) return false
        return parameter.isVal || parameter.isVar
    }

    private fun simplifyImpl(
        context: ResolutionContext, expr: Expression,
        flow0: FlowResult, needsValue: Boolean
    ): FlowResult {
        val block0 = flow0.value ?: return flow0
        return simplifyImpl(context, expr, block0.block, flow0, needsValue)
    }

    private fun simplifyImpl(
        context: ResolutionContext, expr: Expression,
        block0: SimpleBlock, flow0: FlowResult, needsValue: Boolean,
        contextExpr: Expression? = null // for ThisExpression
    ): FlowResult {
        when (expr) {
            is ExpressionList -> return simplifyList(context, expr, block0, flow0, needsValue)
            is YieldExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, true)
                val field1v = field.value ?: return field
                val continueBlock = block0.graph.addNode()
                field1v.block.add(SimpleYield(field1v.value.use(), continueBlock, expr.scope, expr.origin))
                continueBlock.isEntryPoint = true
                return field.withValue(unitInstance(block0.graph, expr), continueBlock)
            }

            is ReturnExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, true, expr)
                val field1v = field.value ?: return field
                return field.joinReturnNoValue(field1v.value.use(), field1v.block)
            }

            is ThrowExpression -> {
                val field = simplifyImpl(context, expr.value, block0, flow0, true)
                val field1v = field.value ?: return field
                return field.joinThrownNoValue(field1v.value.use(), field1v.block)
            }

            is ResolvedCompareOp -> return simplifyCompareOp(context, expr, block0, flow0)
            is ResolvedCallExpression -> return simplifyCall(context, expr, block0, flow0)
            is DynamicMacroExpression -> return simplifyDynamicMacro(context, expr, block0, flow0)

            is SpecialValueExpression -> {
                val type = when (expr.type) {
                    SpecialValue.NULL -> NullType
                    SpecialValue.TRUE, SpecialValue.FALSE -> Types.Boolean
                }
                val dst = block0.field(type, expr)
                block0.add(SimpleSpecialValue(dst, expr.type, expr.scope, expr.origin))
                return flow0.withValue(dst, block0)
            }

            is ThisExpression -> {
                val type = expr.label.typeWithArgs.specialize(context)
                val dst = block0.thisField(
                    type, expr.label, expr.scope, expr.origin,
                    context.specialization, contextExpr
                )
                return flow0.withValue(dst, block0)
            }
            is SuperExpression -> {
                val type = expr.label.typeWithArgs.specialize(context)
                val dst = block0.thisField(
                    type, expr.label, expr.scope, expr.origin,
                    context.specialization, contextExpr
                )
                return flow0.withValue(dst, block0)
            }

            is ResolvedGetFieldExpression -> return simplifyGetField(context, expr, block0, flow0)
            is ResolvedSetFieldExpression -> return simplifySetField(context, expr, block0, flow0)

            is NumberExpression -> {
                val type = TypeResolution.resolveType(context, expr)
                val dst = block0.field(type, expr)
                block0.add(SimpleNumber(dst, expr))
                return flow0.withValue(dst, block0)
            }

            is StringExpression -> {
                val dst = block0.field(Types.String, expr)
                block0.add(SimpleString(dst, expr))
                return flow0.withValue(dst, block0)
            }

            is IsInstanceOfExpr -> {
                val block1 = simplifyImpl(context, expr.value, block0, flow0, true)
                val block1v = block1.value ?: return block1
                val dst = block1v.block.field(Types.Boolean)
                block1v.block.add(createSimpleInstanceOf(dst, block1v.value.use(), expr.type, expr.scope, expr.origin))
                return block1.withValue(dst, block1v.block)
            }

            is GetClassFromTypeExpression -> {
                val dst = block0.field(expr.resolveReturnType(context))
                block0.add(SimpleGetTypeInstance(dst, expr.type, expr.scope, expr.origin))
                return flow0.withValue(dst, block0)
            }

            is GetClassFromValueExpression -> {
                val block1 = simplifyImpl(context, expr.value, block0, flow0, true)
                val block1v = block1.value ?: return block1
                val dst = block0.field(expr.resolveReturnType(context))
                block0.add(SimpleGetTypeFromInstance(dst, block1v.value.use(), expr.scope, expr.origin))
                return flow0.withValue(dst, block0)
            }

            is IfElseBranch -> return simplifyBranch(context, expr, block0, flow0, needsValue)
            is WhileLoop -> return simplifyWhile(context, expr, block0, flow0)
            is DoWhileLoop -> return simplifyDoWhile(context, expr, block0, flow0)
            is TryCatchBlock -> return simplifyTryCatch(context, expr, block0, flow0, needsValue)
            is CheckEqualsOp -> return simplifyCheckEqualsOp(context, expr, block0, flow0)
            is LazyExpression -> return simplifyImpl(context, expr.value, block0, flow0, needsValue)

            is BreakExpression -> return simplifyJump(expr, flow0, block0.graph.breakLabels[expr.label])
            is ContinueExpression -> return simplifyJump(expr, flow0, block0.graph.continueLabels[expr.label])

            // pseudo/placeholder
            is DelegateExpression -> return simplifyImpl(context, expr.value, block0, flow0, needsValue)

            else -> {
                if (!expr.isResolved()) throw IllegalStateException("${expr.javaClass.simpleName} was not resolved")
                throw NotImplementedError("Simplify value ${expr.javaClass.simpleName}: $expr")
            }
        }
    }

    private fun simplifyJump(expr: Expression, flow0: FlowResult, target: SimpleBlock?): FlowResult {
        val block0 = flow0.value ?: return flow0
        check(target != null) { "Failed to resolve jump target to $expr" }
        block0.block.nextBranch = target
        return flow0.withoutValue() // our flow ends here, nothing can come after
    }

    private fun simplifyList(
        context: ResolutionContext,
        expr: ExpressionList,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean
    ): FlowResult {
        val exprScope = expr.scope

        // declare fields -> needed? yes, better like that for some generators
        if (exprScope.isInsideExpression()) {
            for (field in exprScope.fields) {
                if (needsFieldByParameter(field.byParameter) &&
                    // field.originalScope == field.codeScope && // not moved
                    block0.instructions.none { it is SimpleDeclaration && it.name == field.name }
                ) {
                    val type = field.resolveValueType(context)
                    block0.add(SimpleDeclaration(type, field.name, field.scope, field.origin))
                }
            }
        }

        var blockI = flow0
        for (expr in expr.list) {
            val blockIv = blockI.value ?: return blockI
            blockI = simplifyImpl(context, expr, blockIv.block, blockI, needsValue)
        }
        return blockI
    }

    private fun simplifyDynamicMacro(
        context: ResolutionContext,
        expr: DynamicMacroExpression,
        block0: SimpleBlock,
        flow0: FlowResult
    ): FlowResult {
        // (base, block1)
        val block1 = simplifyImpl(context, expr.self, block0, flow0, true)
        val base = block1.value ?: return block1

        // println("Simplified self to ${expr.self} (${expr.self.javaClass.simpleName})")
        var blockI = block1
        val valueParameters = expr.valueParameters.map { param ->
            blockI = simplifyImpl(context, param, blockI.value!!.block, blockI, false)
            blockI.value?.value ?: return blockI
        }

        valueParameters.forEach { it.use() }

        val method0 = expr.method
        val method = method0.resolved
        val block0 = blockI.value!!.block
        val selfExpr = base.value.use()
        // then execute it
        val dst = block0.field(method0.getTypeFromCall())
        val specialization = method0.specialization
        val call = SimpleDynamicMacro(dst, expr, selfExpr.use(), valueParameters, expr.scope, expr.origin)
        return handleThrown(block0, flow0, dst, call, method.getThrownType(specialization))
    }

    private fun simplifyCall(
        context: ResolutionContext,
        expr: ResolvedCallExpression,
        block0: SimpleBlock,
        flow0: FlowResult
    ): FlowResult {

        // (base, block1)
        val block1 = if (expr.self != null) {
            simplifyImpl(context, expr.self, block0, flow0, true, contextExpr = expr)
        } else flow0
        val base = block1.value ?: return block1

        // println("Simplified self to ${expr.self} (${expr.self.javaClass.simpleName})")
        var blockI = block1
        val valueParameters = expr.valueParameters.map { param ->
            blockI = simplifyImpl(context, param, blockI.value!!.block, blockI, false)
            blockI.value?.value ?: return blockI
        }

        val method = expr.callable
        val selfExpr = if (expr.self != null) base.value.use() else null
        val selfIfInsideConstructor = (expr.self as? SuperExpression)?.isThis
        return simplifyCall(
            blockI.value!!.block, blockI, selfExpr, expr.self,
            valueParameters, method, selfIfInsideConstructor,
            expr.scope, expr.origin
        )
    }

    private fun simplifyGetField(
        context: ResolutionContext,
        expr: ResolvedGetFieldExpression,
        block0: SimpleBlock,
        flow0: FlowResult
    ): FlowResult {

        var expr = expr
        var canUseGetter = true
        if (expr.field.isBackingField) {
            canUseGetter = false
            // probably could be smaller and easier...
            val backed = expr.field.resolved.getBackedField()!!
            val realField = backedToRealField(backed, expr.field, expr.context)
            expr = ResolvedGetFieldExpression(
                ThisExpression(backed.ownerScope, expr.scope, expr.origin),
                realField, expr.scope, expr.origin
            )
        }

        val field = expr.field.resolved

        if (field.isObjectInstance()) {
            val dst = block0.field(field.ownerScope.typeWithArgs)
            val value = SimpleGetObject(dst, field.ownerScope, expr.scope, expr.origin)
            block0.add(value)
            return flow0.withValue(dst)
        }

        val valueType = expr.resolveReturnType(context)
        // println("valueType for $expr: $valueType")

        val block1 = simplifyImpl(context, expr.self, block0, flow0, true, expr)
        val block1v = block1.value ?: return block1
        val self = block1v.value

        val useGetter = canUseGetter && (
                expr.field.resolved.isOpen() ||
                        expr.field.resolved.initialValue is DelegateExpression || (
                        !expr.field.isBackingField && (
                                field.hasCustomGetter ||
                                        field.isLateinit() ||
                                        !field.needsToBeStored()
                                )
                        )
                )

        val selfMethod = getMethod(self.type)
        val valueMethod = block0.graph.method

        // todo if we call in an inner method, immediately AST-simplify it, so we know all captured fields

        if (needsCapture(selfMethod, valueMethod, field)) {
            val dst = block0.graph.readCapturedField(valueMethod, field, valueType)
            return block1.withValue(dst, block1v.block)
        }

        val dst = block1v.block.field(valueType)
        if (useGetter) {

            if (field.getter == null) finishGetter(field.ownerScope, field)

            val ownerTypes = (self.type as? ClassType)?.typeParameters ?: ParameterList.emptyParameterList()
            val getter = field.getter ?: throw IllegalStateException("Missing getter for $field")
            val newContext = context.withSpec(Specialization(getter.scope, ownerTypes))
            val resolvedGetter = ResolvedMethod(getter, newContext, expr.scope, MatchScore.zero)
            return simplifyCall(
                block1v.block, block1, self, expr.self,
                emptyList(), resolvedGetter,
                null, expr.scope, expr.origin
            )
        } else {

            // println("Creating SimpleGetField for $field, self: ${expr.self}")

            var self = self

            // add extra getters for inner classes
            if (self.type is ClassType && self.type.clazz.isInnerClassOf(field.ownerScope)) {
                val clazz = self.type.clazz
                val outerField = clazz.fields.firstOrNull { it.name == OUTER_FIELD_NAME }
                    ?: throw IllegalStateException("Missing $OUTER_FIELD_NAME field in $clazz for $field")
                val selfDst = block1v.block.field(outerField.valueType!!.specialize(context))
                // println("Adding self = self.$field for $clazz -> ${outerField.valueType}")
                val getter = SimpleGetField(
                    selfDst, self.use(), outerField,
                    expr.field.specialization, expr.scope, expr.origin
                )
                block1v.block.add(getter)
                self = selfDst
            }

            val getter = SimpleGetField(
                dst, self.use(), field,
                expr.field.specialization, expr.scope, expr.origin
            )
            block1v.block.add(getter)
            return block1.withValue(dst, block1v.block)
        }
    }

    private fun simplifySetField(
        context: ResolutionContext,
        expr: ResolvedSetFieldExpression,
        block0: SimpleBlock,
        flow0: FlowResult
    ): FlowResult {

        var expr = expr
        var canUseSetter = true
        if (expr.field.isBackingField) {
            canUseSetter = false
            // probably could be smaller and easier...
            val backed = expr.field.resolved.getBackedField()!!
            val realField = backedToRealField(backed, expr.field, expr.context)
            expr = ResolvedSetFieldExpression(
                ThisExpression(backed.ownerScope, expr.scope, expr.origin),
                realField, expr.value, expr.scope, expr.origin
            )
        }

        val field = expr.field.resolved

        val block1 = simplifyImpl(context, expr.self, block0, flow0, true)
        val block1v = block1.value ?: return block1
        val self = block1v.value

        val block2 = simplifyImpl(context, expr.value, block1, true)
        val block2v = block2.value ?: return block2
        val value = block2v.value

        val useSetter = canUseSetter && (
                expr.field.resolved.isOpen() ||
                        expr.field.resolved.initialValue is DelegateExpression || (
                        !expr.field.isBackingField &&
                                (field.hasCustomSetter || !field.needsToBeStored())
                        )
                )

        val selfMethod = getMethod(self.type)
        val valueMethod = block0.graph.method

        // todo if we call in an inner method, immediately AST-simplify it, so we know all captured fields

        if (needsCapture(selfMethod, valueMethod, field)) {
            block0.graph.onCapturedField(field)

            TODO("Set captured field somehow...")
        }

        if (useSetter) {
            val ownerTypes = (self.type as? ClassType)?.typeParameters ?: ParameterList.emptyParameterList()
            val setter = field.setter ?: throw IllegalStateException("Missing setter for $field")
            val newContext = context.withSpec(Specialization(setter.scope, ownerTypes))
            val resolvedSetter = ResolvedMethod(setter, newContext, expr.scope, MatchScore.zero)
            return simplifyCall(
                block2v.block, block2, self, expr.self,
                listOf(value), resolvedSetter,
                null, expr.scope, expr.origin
            )
        } else {
            block2v.block.add(
                SimpleSetField(
                    self.use(),
                    field,
                    value.use(),
                    expr.field.specialization,
                    expr.scope,
                    expr.origin
                )
            )
            return block2.withValue(unitInstance(block0.graph, expr), block2v.block)
        }
    }

    fun backedToRealField(backed: Field, backing: ResolvedField, context: ResolutionContext): ResolvedField {
        val realSpec = Specialization(
            backed.fieldScope,
            context.specialization.typeParameters
        )
        return ResolvedField(
            backed, context.withSpec(realSpec),
            backing.codeScope, backing.matchScore
        )
    }

    fun getMethod(type: Type): MethodLike? {
        val type = type.resolvedName.resolve()
        if (type !is ClassType) return null

        var scope = type.clazz
        while (true) {
            val method = scope.selfAsMethod
            if (method != null) return method

            scope = scope.parentIfSameFile ?: return null
        }
    }

    private fun needsCapture(selfMethod: MethodLike?, valueMethod: MethodLike, field: Field): Boolean {
        return selfMethod != null && selfMethod != valueMethod &&
                valueMethod.scope.parent?.isObjectLike() != true &&
                !field.ownerScope.isObjectLike()
    }

    private fun simplifyCompareOp(
        context: ResolutionContext,
        expr: ResolvedCompareOp,
        block0: SimpleBlock,
        flow0: FlowResult
    ): FlowResult {
        val block1 = simplifyImpl(context, expr.left, block0, flow0, true)
        val block1v = block1.value ?: return block1
        val left = block1v.value

        val block2 = simplifyImpl(context, expr.right, block1, false)
        val block2v = block2.value ?: return block2
        val right = block2v.value

        val tmp = block2v.block.field(Types.Int)
        val method = expr.callable.resolved
        val specialization = expr.callable.specialization
        val call = SimpleCall(
            tmp, method, left.use(), specialization,
            emptyList(), listOf(right.use()), emptyList(),
            expr.scope, expr.origin
        )

        val block3 = handleThrown(
            block2v.block, block2,
            tmp, call, method.getThrownType(specialization)
        )
        val block3v = block3.value ?: return block3

        val dst = block3v.block.field(Types.Boolean)
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
        block0: SimpleBlock,
        flow0: FlowResult,
    ): FlowResult {
        val block1 = simplifyImpl(context, expr.left, block0, flow0, true)
        val block1v = block1.value ?: return block1

        val block2 = simplifyImpl(context, expr.right, block1, true)
        val block2v = block2.value ?: return block2

        val dst = block2v.block.field(Types.Boolean)
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
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean
    ): FlowResult {
        check(block0 == flow0.value?.block)
        val body = simplifyImpl(context, expr.tryBody, flow0, false)
        val allCaught = simplifyCatches(context, expr, body, needsValue)
        return simplifyFinally(context, expr, allCaught)
    }

    private fun simplifyCatches(
        context: ResolutionContext,
        expr: TryCatchBlock,
        flow0: FlowResult,
        needsValue: Boolean
    ): FlowResult {
        var blockI = flow0
        for (catch in expr.catches) {
            blockI = simplifyCatch(context, expr, catch, blockI, needsValue)
        }
        return blockI
    }

    private fun simplifyCatch(
        context: ResolutionContext,
        expr: TryCatchBlock,
        catch: Catch,
        flow0: FlowResult,
        needsValue: Boolean
    ): FlowResult {

        val block0 = flow0.thrown ?: return flow0
        val block0b = block0.block

        val thrownType = catch.parameter.type
        val instanceOfField = block0b.field(Types.Boolean)
        val instanceCheck = createSimpleInstanceOf(instanceOfField, block0.value, thrownType, expr.scope, catch.origin)

        val alwaysHandled = instanceCheck is SimpleSpecialValue && instanceCheck.type == SpecialValue.TRUE
        val neverHandled = instanceCheck is SimpleSpecialValue && instanceCheck.type == SpecialValue.FALSE
        println("Catch handling($instanceCheck by ${block0.value} is $thrownType), body: ${catch.body}")

        return when {
            alwaysHandled -> simplifyCatchHandleBranch(context, expr, catch, needsValue, block0, block0b)
            neverHandled -> simplifyCatchContinueBranch(flow0, block0, block0b)
            else -> {
                block0b.add(instanceCheck)

                val graph = block0b.graph
                val ifBlock = graph.addNode()
                val elseBlock = graph.addNode()
                block0b.branchCondition = instanceOfField.use()
                block0b.ifBranch = ifBlock
                block0b.elseBranch = elseBlock

                val ifFlow = simplifyCatchHandleBranch(context, expr, catch, needsValue, block0, ifBlock)
                val elseFlow = simplifyCatchContinueBranch(flow0, block0, elseBlock)
                ifFlow.joinWith(elseFlow)
            }
        }
    }

    private fun simplifyCatchHandleBranch(
        context: ResolutionContext,
        expr: TryCatchBlock,
        catch: Catch,
        needsValue: Boolean,
        block0: Flow,
        ifBlock: SimpleBlock,
    ): FlowResult {
        val ifFlow = FlowResult(Flow(unitInstance(ifBlock.graph, expr), ifBlock), null, null)
        val methodType = catch.parameter.scope.typeWithArgs
        val selfField = ifBlock.thisField(
            methodType, catch.parameter.scope, expr.scope, expr.origin,
            context.specialization, null
        )
        val thrownField = catch.parameter.getOrCreateField(null, Flags.NONE)
        val spec = Specialization(thrownField.fieldScope, emptyParameterList())
        ifBlock.add(SimpleSetField(selfField, thrownField, block0.value, spec, expr.scope, expr.origin))
        return simplifyImpl(context, catch.body, ifBlock, ifFlow, needsValue)
    }

    private fun simplifyCatchContinueBranch(
        flow0: FlowResult,
        block0: Flow,
        elseBlock: SimpleBlock,
    ): FlowResult {
        return flow0.withThrown(block0.value, elseBlock)
    }

    private fun simplifyFinally(
        context: ResolutionContext,
        expr: TryCatchBlock,
        flow0: FlowResult
    ): FlowResult {

        val finally = expr.finally ?: return flow0

        // apply finally-block to normal execution flow
        val value = if (flow0.value != null) {
            simplifyImpl(context, finally, flow0, false)
                .withValue(flow0.value.value)
        } else voidResult

        // apply finally-block to returned values
        val returned = if (flow0.returned != null) {
            val returnFlow = flow0.returnToValue()
            simplifyImpl(context, finally, returnFlow, false)
                .valueToReturn(flow0.returned)
        } else null

        // apply finally-block to thrown values
        val thrown = if (flow0.thrown != null) {
            val throwFlow = flow0.thrownToValue()
            simplifyImpl(context, finally, throwFlow, false)
                .valueToThrown(flow0.thrown)
        } else null

        return value
            .joinError(returned)
            .joinError(thrown)
    }

    private fun simplifyWhile(
        context: ResolutionContext,
        expr: WhileLoop,
        block0: SimpleBlock,
        flow0: FlowResult,
    ): FlowResult {

        val graph = block0.graph
        val conditionBlock = block0.nextOrSelfIfEmpty()
        val bodyBlock = graph.addNode()
        val afterBlock = graph.addNode()
        val elseBlock = if (expr.elseBranch != null) graph.addNode() else null

        val labelScope = expr.body.scope
        graph.breakLabels[labelScope] = afterBlock
        graph.continueLabels[labelScope] = conditionBlock

        val unit = unitInstance(graph, expr)
        val beforeFlow0 = flow0.withValue(unit, conditionBlock)
        // add condition and jump to insideBlock
        // (condition, beforeBlock1)
        val beforeBlock1 = simplifyImpl(context, expr.condition, conditionBlock, beforeFlow0, true)
        val beforeBlock1v = beforeBlock1.value ?: return beforeBlock1
        val condition = beforeBlock1v.value
        beforeBlock1v.block.branchCondition = condition.use()
        beforeBlock1v.block.ifBranch = bodyBlock
        beforeBlock1v.block.elseBranch = elseBlock ?: afterBlock

        if (elseBlock != null) {
            // todo test this
            val elseBlock1 = simplifyImpl(context, expr.elseBranch!!, elseBlock, beforeBlock1, false)
            elseBlock1.value?.block?.nextBranch = afterBlock
        }

        // add body to insideBlock
        val insideFlow0 = beforeBlock1.withValue(unit, bodyBlock)
        val insideBlock1 = simplifyImpl(context, expr.body, bodyBlock, insideFlow0, false)
        // continue, if possible
        insideBlock1.value?.block?.nextBranch = conditionBlock

        return beforeBlock1.withValue(unit, afterBlock)
    }

    private fun simplifyDoWhile(
        context: ResolutionContext,
        expr: DoWhileLoop,
        block0: SimpleBlock,
        flow0: FlowResult,
    ): FlowResult {

        val graph = block0.graph
        val bodyBlock = block0.nextOrSelfIfEmpty()
        val conditionBlock = graph.addNode()
        val afterBlock = graph.addNode()

        val labelScope = expr.body.scope
        graph.breakLabels[labelScope] = afterBlock
        graph.continueLabels[labelScope] = conditionBlock

        val unit = unitInstance(graph, expr)
        val insideFlow0 = flow0.withValue(unit, bodyBlock)
        val insideBlock1 = simplifyImpl(context, expr.body, bodyBlock, insideFlow0, false)
        var flow1 = insideFlow0.joinError(insideBlock1)
        if (insideBlock1.value != null) {
            insideBlock1.value.block.nextBranch = conditionBlock

            // add condition and jump to insideBlock
            val flow1d = flow1.withValue(unit, conditionBlock)
            val decideBlock1i = simplifyImpl(context, expr.condition, conditionBlock, flow1d, true)
            if (decideBlock1i.value != null) {
                val decideBlock1 = decideBlock1i.value.block
                decideBlock1.branchCondition = decideBlock1i.value.value.use()
                decideBlock1.ifBranch = bodyBlock
                decideBlock1.elseBranch = afterBlock
            }
            flow1 = flow1.joinError(decideBlock1i)
        }

        // Unit, because no return value is supported
        return flow1.withValue(unit, afterBlock)
    }

    private fun simplifyBranch(
        context: ResolutionContext,
        expr: IfElseBranch,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
    ): FlowResult {

        val block1 = simplifyImpl(context, expr.condition, block0, flow0, true)
        val block1v = block1.value ?: return block1
        val condition = block1v.value

        // todo when the condition to a branch is a simple boolean, skip evaluating the other branch!

        val graph = block0.graph
        val ifBlock = graph.addNode()
        val elseBlock = graph.addNode()

        block1v.block.branchCondition = condition.use()
        block1v.block.ifBranch = ifBlock
        block1v.block.elseBranch = elseBlock

        val unit = unitInstance(graph, expr)
        val ifFlow = block1.withValue(unit, ifBlock)
        val elseFlow = block1.withValue(unit, elseBlock)

        if (expr.elseBranch == null) {
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, ifFlow, needsValue)
            ifValue.value?.block?.nextBranch = elseBlock

            return elseFlow.joinError(ifValue)
                .withValue(unit, elseBlock)
        } else {
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, ifFlow, needsValue)
            val elseValue = simplifyImpl(context, expr.elseBranch, elseBlock, elseFlow, needsValue)
            return ifValue.joinWith(elseValue)
        }
    }

    private fun simplifyCall(
        block0: SimpleBlock,
        flow0: FlowResult,

        selfField: SimpleField?,
        selfExpr: Expression?,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Long
    ): FlowResult {
        for (param in valueParameters) param.use()
        return when (val method = method0.resolved) {
            is Method -> {
                // then execute it
                val dst = block0.field(method0.getTypeFromCall())
                val specialization = method0.specialization
                val selfField = selfField!!.use()
                val call = if (selfExpr is SuperExpression) {
                    val methodMap = FullMap<ClassType, MethodLike>(method)
                    SimpleCall(dst, method, methodMap, selfField, specialization, valueParameters, scope, origin)
                } else {
                    SimpleCall(dst, method, selfField, specialization, valueParameters, scope, origin)
                }
                handleThrown(block0, flow0, dst, call, method.getThrownType(specialization))
            }
            is Constructor -> createConstructorInvocation(
                block0, flow0, method, valueParameters,
                method0, selfIfInsideConstructor, scope, origin
            )
            else -> throw NotImplementedError("Simplify call $method at ${resolveOrigin(origin)}")
        }
    }

    private fun createConstructorInvocation(
        block0: SimpleBlock,
        flow0: FlowResult,

        method: Constructor,
        valueParameters: List<SimpleField>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Long
    ): FlowResult {
        return if (selfIfInsideConstructor != null) {
            val graph = block0.graph
            val unit = unitInstance(graph, scope, origin)

            val selfScope = (graph.method as Constructor).classScope
            val selfType = selfScope.typeWithArgs.specialize(method0.specialization)
            val self = block0.thisField(selfType, selfScope, scope, origin, method0.specialization, null)

            val constructor = SimpleSelfConstructor(
                unit, selfIfInsideConstructor,
                self.use(), method, method0.specialization, valueParameters, scope, origin
            )

            handleThrown(
                block0, flow0, unit, constructor,
                method.getThrownType(method0.specialization)
            )
        } else {
            val dst = block0.field(method0.getTypeFromCall())
            var selfType = method.selfTypeI.specialize(method0.specialization) as ClassType
            if (selfType.typeParameters == null) {
                selfType = selfType.clazz.typeWithArgs.specialize(method0.specialization) as ClassType
            }
            // todo allocation could fail, too...
            block0.add(SimpleAllocateInstance(dst, selfType, valueParameters, scope, origin))
            val unusedTmp = block0.field(Types.Unit)
            val specialization = method0.specialization
            val call = SimpleCall(unusedTmp, method, dst.use(), specialization, valueParameters, scope, origin)
            LOGGER.info("Finding throw type for $method0")
            handleThrown(block0, flow0, dst, call, method.getThrownType(specialization))
        }
    }

    fun handleThrown(
        block0: SimpleBlock, flow0: FlowResult,
        result: SimpleField, callable: SimpleCallable, thrownType: Type
    ): FlowResult {

        block0.add(callable)

        val flow1 = flow0.withValue(result, block0)
        if (thrownType == Types.Nothing) return flow1

        val throwBlock = block0.graph.addNode()
        val throwField = throwBlock.field(thrownType)
        val throwFlow = Flow(throwField, throwBlock)
        callable.onThrown = throwFlow

        val flow2 = flow1.joinError(throwFlow)
            .withValue(result, block0)
        // println("joining: $flow1 x $throwFlow = $flow2")
        return flow2
    }

    fun reorderResolveParameters(
        context: ResolutionContext,
        src: List<NamedParameter>, targetParams: List<Parameter>,
        scope: Scope, origin: Long
    ): List<Expression> {
        return reorderParameters(src, targetParams, scope, origin).mapIndexed { index, param ->
            param.resolve(context.withTargetType(targetParams[index].type))
        }
    }

    fun reorderParameters(
        src: List<NamedParameter>, dst: List<Parameter>,
        scope: Scope, origin: Long
    ): List<Expression> {
        return resolveNamedParameters(dst, src, scope, origin)
            ?: throw IllegalStateException("Failed to fill in call parameters at ${resolveOrigin(origin)}")
    }

}