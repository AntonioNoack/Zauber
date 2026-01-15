package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignIfMutableExpr
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.ConstructorExpression
import me.anno.zauber.ast.rich.expression.unresolved.DotExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.LambdaExpression
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.ast.rich.expression.unresolved.UnresolvedFieldExpression
import me.anno.zauber.ast.simple.controlflow.SimpleBranch
import me.anno.zauber.ast.simple.controlflow.SimpleGoto
import me.anno.zauber.ast.simple.controlflow.SimpleLoop
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.FieldResolver
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallable
import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.NullType

object ASTSimplifier {

    val voidField = SimpleField(UnitType, Ownership.COMPTIME, -1)
    val booleanOwnership = Ownership.COMPTIME

    // todo inline functions
    // todo calculate labels for break/continue
    // todo calculate what errors a function throws,
    //  and handle all possibilities after each call

    fun simplify(context: ResolutionContext, expr: Expression): SimpleGraph {
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
                var result = voidField
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
                /*for (childScope in exprScope.children) {
                    for (field in childScope.fields) {
                        if (needsFieldByParameter(field.byParameter) &&
                            field.codeScope != field.originalScope &&
                            currBlock.instructions.none { it is SimpleDeclaration && it.name == field.name }
                        ) {
                            val type = field.resolveValueType(context)
                            currBlock.add(SimpleDeclaration(type, field.name, field.codeScope, field.origin))
                        }
                    }
                }*/
                for (expr in expr.list) {
                    result = simplifyImpl(context, expr, currBlock, graph, needsValue)
                        ?: return null
                }
                result
            }
            is ThrowExpression -> {
                // todo we need to check all handlers...
                //   and finally, we need to exit
                voidField
            }
            is ReturnExpression -> {
                // this is the standard way to exit,
                //  but todo we also want yields for async functions and sequences
                val field = simplifyImpl(context, expr.value, currBlock, graph, true)
                if (field != null) currBlock.add(SimpleReturn(field.use(), expr.scope, expr.origin))
                // todo else write unreachable?
                voidField
            }

            is AssignmentExpression -> {
                // todo get all stuff on the left
                when (val dstExpr = expr.variableName) {
                    is FieldExpression -> {
                        val newValue = simplifyImpl(context, expr.newValue, currBlock, graph, true)
                            ?: return null
                        val self: SimpleField? =
                            null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                        // todo we should call the setter, if there is one
                        currBlock.add(SimpleSetField(self, dstExpr.field, newValue.use(), expr.scope, expr.origin))
                        voidField
                    }
                    is UnresolvedFieldExpression -> {
                        val newValue = simplifyImpl(context, expr.newValue, currBlock, graph, true)
                            ?: return null
                        val self: SimpleField? =
                            null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                        // todo we should call the setter, if there is one
                        val field0 = dstExpr.resolveField(context.withAllowTypeless(false))
                            ?: throw IllegalStateException("[UnResField2] Failed to resolve field from $dstExpr in $context")
                        val field = field0.resolved
                        currBlock.add(SimpleSetField(self, field, newValue.use(), expr.scope, expr.origin))
                        voidField
                    }
                    else -> throw NotImplementedError("Implement assignment to ${expr.variableName} (${expr.variableName.javaClass.simpleName})")
                }
            }
            is CompareOp -> {
                val base = simplifyImpl(context, expr.value, currBlock, graph, true) ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                currBlock.add(SimpleCompare(dst, base.use(), expr.type, expr.scope, expr.origin))
                dst
            }
            is NamedCallExpression -> {
                val calleeType = expr.calculateBaseType(context)

                // todo type-args may be needed for type resolution
                val valueParameters = resolveValueParameters(context, expr.valueParameters)

                val constructor = null
                val context = context.withSelfType(calleeType)
                val method = resolveCallable(
                    context, expr.scope,
                    expr.name, expr.nameAsImport, constructor,
                    expr.typeParameters, valueParameters, expr.origin,
                ) ?: MethodResolver.printScopeForMissingMethod(
                    context, expr, expr.name,
                    expr.typeParameters, valueParameters
                )
                simplifyCall(
                    context, currBlock, graph, expr.base,
                    expr.typeParameters, expr.valueParameters, method, null,
                    expr.scope, expr.origin
                )
            }
            is CallExpression -> {
                val method = expr.resolveCallable(context)
                simplifyCall(
                    context, currBlock, graph, expr.base,
                    expr.typeParameters, expr.valueParameters, method, null,
                    expr.scope, expr.origin
                )
            }
            is ConstructorExpression -> {
                val method = expr.resolveMethod(context)
                simplifyCall(
                    context, currBlock, graph, null,
                    expr.typeParameters, expr.valueParameters,
                    method, expr.selfIfInsideConstructor,
                    expr.scope, expr.origin
                )
            }
            is SpecialValueExpression -> {
                val type = when (expr.type) {
                    SpecialValue.NULL -> NullType
                    SpecialValue.TRUE, SpecialValue.FALSE -> BooleanType
                    SpecialValue.THIS -> context.selfType
                        ?: throw IllegalStateException("Missing selfType for $this in ${resolveOrigin(expr.origin)}")
                    SpecialValue.SUPER -> throw IllegalStateException("Cannot store super in a field")
                }
                val dst = currBlock.field(type)
                currBlock.add(SimpleSpecialValue(dst, expr))
                dst
            }
            is UnresolvedFieldExpression -> {
                val field = expr.resolveField(context)
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                val dst = currBlock.field(field.getValueType())
                currBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                dst
            }
            is FieldExpression -> simplifyFieldExpr(currBlock, context, expr)
            is MemberNameExpression -> {
                val field = resolveField(context, expr.scope, expr.name, expr.nameAsImport, null, expr.origin)
                    ?: throw IllegalStateException("Missing field '${expr.name}'")
                val valueType = field.getValueType()
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                val dst = currBlock.field(valueType)
                currBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                dst
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
            is DotExpression -> {
                val left = expr.left
                val baseType = expr.getBaseType(context)

                val right = expr.right
                when {
                    expr.isFieldType() -> {
                        val field = expr.resolveField(context, baseType)
                            ?: TODO("Unresolved field for field type: (${expr.javaClass.simpleName}) $expr")
                        val self = simplifyImpl(context, left, currBlock, graph, true)
                        val dst = currBlock.field(field.getValueType())
                        currBlock.add(SimpleGetField(dst, self?.use(), field.resolved, expr.scope, expr.origin))
                        dst
                    }
                    expr.isMethodType() -> {
                        right as CallExpression
                        val method = expr.resolveCallable(context, baseType)
                        simplifyCall(
                            context, currBlock, graph, left,
                            right.typeParameters, right.valueParameters, method, null,
                            expr.scope, expr.origin
                        )
                    }
                    else -> {
                        TODO("Resolve DotExpression with type ${expr.right.javaClass.simpleName}")
                    }
                }
            }
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
            is AssignIfMutableExpr -> {
                val leftValue = simplifyImpl(context, expr.left, currBlock, graph, true) ?: return null
                val rightValue = simplifyImpl(context, expr.right, currBlock, graph, true) ?: return null
                val (_, _, dstField, method) = expr.resolveMethod(context)
                val callResult = currBlock.field(method.getTypeFromCall())
                val call = SimpleCall(
                    callResult,
                    method.resolved,
                    leftValue.use(),
                    listOf(rightValue.use()),
                    expr.scope,
                    expr.origin
                )
                currBlock.add(call)
                if (dstField != null) {
                    // todo reassign field, if given
                    val newValue = callResult
                    val self: SimpleField? =
                        null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                    // todo we should call the setter, if there is one
                    currBlock.add(SimpleSetField(self, dstField, newValue.use(), expr.scope, expr.origin))
                }
                voidField
            }
            is LambdaExpression -> {
                // todo if target is an inline function,
                //  inline it here
                // todo if not, create an instance accordingly

                TODO("Simplify lambda ${expr.javaClass.simpleName}: $expr")
            }
            else -> TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
        }
    }

    fun simplifyFieldExpr(currBlock: SimpleBlock, context: ResolutionContext, expr: FieldExpression): SimpleField? {
        val field = expr.field
        val valueType = field.resolveValueType(context)
        val self: SimpleField? =
            null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
        val dst = currBlock.field(valueType)
        currBlock.add(SimpleGetField(dst, self, field, expr.scope, expr.origin))
        return dst
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
        return voidField // no return value is supported
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
        return voidField // no return value is supported
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
            return voidField
        } else {
            val scope = expr.scope
            val origin = expr.origin
            val ifBlock = graph.addBlock(scope, origin)
            val elseBlock = graph.addBlock(scope, origin)
            val ifValue = simplifyImpl(context, expr.ifBranch, ifBlock, graph, true)
            val elseValue = simplifyImpl(context, expr.elseBranch, elseBlock, graph, true)
            currBlock.add(SimpleBranch(condition.use(), ifBlock, elseBlock, scope, origin))
            return if (!needsValue) voidField else {
                if (ifValue != null && elseValue != null) {
                    currBlock.add(SimpleMerge(dst, ifBlock, ifValue.use(), elseBlock, elseValue.use(), expr))
                    dst
                } else (ifValue ?: elseValue)?.use()
            }
        }
    }

    private fun simplifyCall(
        context: ResolutionContext,
        currBlock: SimpleBlock,
        graph: SimpleGraph,

        selfExpr: Expression?,
        typeParameters: List<Type>?,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
        selfIfInsideConstructor: Boolean?,

        scope: Scope,
        origin: Int
    ): SimpleField? {
        when (val method = method0.resolved) {
            is Method -> {
                selfExpr!!
                val base = simplifyImpl(context, selfExpr, currBlock, graph, true) ?: return null
                val params = reorderParameters(valueParameters, method.valueParameters, scope, origin)
                    .map { parameter ->
                        simplifyImpl(context, parameter, currBlock, graph, true)
                            ?: return null
                    }
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                for (param in params) param.use()
                currBlock.add(SimpleCall(dst, method, base.use(), params, scope, origin))
                return dst
            }
            is Constructor -> {
                // base is a type
                val params =
                    reorderParameters(
                        valueParameters,
                        method.valueParameters,
                        scope, origin
                    ).map { parameter ->
                        simplifyImpl(context, parameter, currBlock, graph, true)
                            ?: return null
                    }
                // then execute it
                for (param in params) param.use()
                if (selfIfInsideConstructor != null) {
                    currBlock.add(
                        SimpleSelfConstructor(
                            selfIfInsideConstructor,
                            method, params, scope, origin
                        )
                    )
                    return voidField
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
                    context, currBlock, graph, fieldExpr,
                    emptyList() /* the type is in the class, not the invocation */, valueParameters,
                    method0, null,
                    scope, origin
                )
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