package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.controlflow.WhileLoop
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.controlflow.SimpleBranch
import me.anno.zauber.ast.simple.controlflow.SimpleGoto
import me.anno.zauber.ast.simple.controlflow.SimpleLoop
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallable
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.NullType

object ASTSimplifier {

    val voidField = SimpleField(UnitType, Ownership.COMPTIME, -1)
    val booleanOwnership = Ownership.COMPTIME

    fun simplify(context: ResolutionContext, expr: Expression): SimpleGraph {
        val graph = SimpleGraph(expr.scope, expr.origin)
        simplifyImpl(context, expr, graph.startBlock, graph, false)
        return graph
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
                if (field != null) currBlock.add(SimpleReturn(field, expr.scope, expr.origin))
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
                        currBlock.add(SimpleSetField(self, dstExpr.field, newValue, expr.scope, expr.origin))
                        voidField
                    }
                    is UnresolvedFieldExpression -> {
                        val newValue = simplifyImpl(context, expr.newValue, currBlock, graph, true)
                            ?: return null
                        val self: SimpleField? =
                            null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                        // todo we should call the setter, if there is one
                        val field = dstExpr.resolveField(context)!!.resolved
                        currBlock.add(SimpleSetField(self, field, newValue, expr.scope, expr.origin))
                        voidField
                    }
                    else -> throw NotImplementedError("Implement assignment to ${expr.variableName} (${expr.variableName.javaClass.simpleName})")
                }
            }
            is CompareOp -> {
                val base = simplifyImpl(context, expr.value, currBlock, graph, true) ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                currBlock.add(SimpleCompare(dst, base, expr.type, expr.scope, expr.origin))
                dst
            }
            is NamedCallExpression -> {
                val calleeType = TypeResolution.resolveType(
                    /* target lambda type seems not deductible */
                    context.withTargetType(null),
                    expr.base,
                )

                // todo type-args may be needed for type resolution
                val valueParameters = resolveValueParameters(context, expr.valueParameters)

                val constructor = null
                val method = resolveCallable(
                    context.withSelfType(calleeType),
                    expr, expr.name, constructor,
                    expr.typeParameters, valueParameters
                )
                simplifyCall(context, expr, currBlock, graph, expr.base, expr.valueParameters, method)
            }
            is CallExpression -> {
                val method = expr.resolveMethod(context)
                simplifyCall(context, expr, currBlock, graph, expr.base, expr.valueParameters, method)
            }
            is SpecialValueExpression -> {
                val type = when (expr.value) {
                    SpecialValue.NULL -> NullType
                    else -> TODO("Resolve type of $expr")
                }
                val dst = currBlock.field(type)
                currBlock.add(SimpleSpecialValue(dst, expr))
                dst
            }
            is UnresolvedFieldExpression -> {
                val field = expr.resolveField(context)
                    ?: throw IllegalStateException("Failed to resolve field $expr")
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                val dst = currBlock.field(field.getValueType(context))
                currBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                dst
            }
            is FieldExpression -> {
                val field = expr.field
                val valueType = field.deductValueType(context)
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                val dst = currBlock.field(valueType)
                currBlock.add(SimpleGetField(dst, self, field, expr.scope, expr.origin))
                dst
            }
            is NumberExpression -> {
                val dst = currBlock.field(expr.resolvedType!!)
                currBlock.add(SimpleNumber(dst, expr))
                dst
            }
            is StringExpression -> {
                val dst = currBlock.field(StringType, Ownership.COMPTIME)
                currBlock.add(SimpleString(dst, expr))
                dst
            }
            is IsInstanceOfExpr -> {
                val src = simplifyImpl(context, expr.instance, currBlock, graph, true)
                    ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                currBlock.add(SimpleInstanceOf(dst, src, expr.type, expr.scope, expr.origin))
                dst
            }
            is IfElseBranch -> simplifyBranch(context, expr, currBlock, graph, needsValue)
            is WhileLoop -> simplifyLoop(context, expr, currBlock, graph, needsValue)
            is DotExpression -> {
                val left = expr.left
                val baseType = expr.getBaseType(context)

                val right = expr.right
                when {
                    expr.isFieldType() -> {
                        val field = expr.resolveField(context, baseType)
                            ?: TODO("Unresolved field for field type")
                        val self = simplifyImpl(context, left, currBlock, graph, true)
                        val dst = currBlock.field(field.getValueType(context))
                        currBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                        dst
                    }
                    expr.isMethodType() -> {
                        right as CallExpression
                        val method = expr.resolveCallable(context, baseType)
                        simplifyCall(context, expr, currBlock, graph, left, right.valueParameters, method)
                    }
                    else -> {
                        TODO("Resolve DotExpression with type ${expr.right.javaClass.simpleName}")
                    }
                }
            }
            is ImportedExpression -> {
                val import = expr.nameAsImport
                when (import.scopeType) {
                    ScopeType.OBJECT -> {
                        val field = import.objectField
                            ?: throw IllegalStateException("Missing object field for ${import.pathStr}")
                        val valueType = field.deductValueType(context)
                        val dst = currBlock.field(valueType)
                        currBlock.add(SimpleGetField(dst, self = null, field, expr.scope, expr.origin))
                        dst
                    }
                    else -> {
                        TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
                    }
                }
            }
            is CheckEqualsOp -> {
                val a = simplifyImpl(context, expr.left, currBlock, graph, true) ?: return null
                val b = simplifyImpl(context, expr.right, currBlock, graph, true) ?: return null
                val dst = currBlock.field(BooleanType, booleanOwnership)
                if (expr.byPointer) {
                    SimpleCheckIdentical(dst, a, b, expr.negated, expr.scope, expr.origin)
                } else {
                    SimpleCheckEquals(dst, a, b, expr.negated, expr.scope, expr.origin)
                }
                dst
            }
            else -> TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
        }
    }

    private fun simplifyLoop(
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
        graph.breakLabels[label] = afterBlock
        graph.continueLabels[label] = beforeBlock

        // add condition and jump to insideBlock
        val condition = simplifyImpl(context, expr.condition.not(), insideBlock, graph, needsValue)
            ?: throw IllegalStateException("Condition should return a boolean, not Nothing")
        insideBlock.add(SimpleGoto(condition, afterBlock, scope, origin))

        // add body to insideBlock
        simplifyImpl(context.withCodeScope(expr.body.scope), expr.body, insideBlock, graph, needsValue)

        val loop = SimpleLoop(insideBlock, scope, origin)
        currBlock.add(beforeBlock) // as a label
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
        val scope = expr.scope
        val origin = expr.origin
        val ifBlock = graph.addBlock(scope, origin)
        val elseBlock = graph.addBlock(scope, origin)
        val ifValue = simplifyImpl(context.withCodeScope(expr.ifBranch.scope), expr, ifBlock, graph, true)
        val elseValue = simplifyImpl(context.withCodeScope(expr.elseBranch!!.scope), expr, elseBlock, graph, true)
        currBlock.add(SimpleBranch(condition, ifBlock, elseBlock, scope, origin))
        return if (ifValue != null && elseValue != null) {
            currBlock.add(SimpleMerge(dst, ifBlock, ifValue, elseBlock, elseValue, expr))
            dst
        } else ifValue ?: elseValue
    }

    private fun simplifyCall(
        context: ResolutionContext,
        expr: Expression,
        currBlock: SimpleBlock,
        graph: SimpleGraph,

        selfExpr: Expression,
        valueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
    ): SimpleField? {
        when (val method = method0.resolved) {
            is Method -> {
                val base = simplifyImpl(context, selfExpr, currBlock, graph, true)
                    ?: return null
                val params =
                    reorderParameters(valueParameters, method.valueParameters, expr.scope, expr.origin)
                        .map { parameter ->
                            simplifyImpl(context, parameter, currBlock, graph, true)
                                ?: return null
                        }
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                currBlock.add(SimpleCall(dst, method, base, params, expr.scope, expr.origin))
                return dst
            }
            is Constructor -> {
                // base is a type
                val params =
                    reorderParameters(
                        valueParameters,
                        method.valueParameters,
                        expr.scope, expr.origin
                    ).map { parameter ->
                        simplifyImpl(context, parameter, currBlock, graph, true)
                            ?: return null
                    }
                // then execute it
                val dst = currBlock.field(method0.getTypeFromCall())
                currBlock.add(SimpleConstructor(dst, method, params, expr.scope, expr.origin))
                return dst
            }
            else -> TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
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