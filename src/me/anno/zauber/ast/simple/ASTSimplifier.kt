package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ThrowExpression
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.controlflow.SimpleBranch
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.typeresolution.CallWithNames.resolveNamedParameters
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.typeresolution.TypeResolution.resolveValueParameters
import me.anno.zauber.typeresolution.members.MethodResolver.resolveCallable
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Scope

object ASTSimplifier {

    val voidField = SimpleField(-1)

    fun simplify(context: ResolutionContext, expr: Expression): SimpleGraph {
        val graph = SimpleGraph()
        simplifyImpl(context, expr, graph.entry, graph, false)
        return graph
    }

    private fun simplifyImpl(
        context: ResolutionContext,
        expr: Expression,
        addToBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean
    ): SimpleField? {
        return when (expr) {
            is ExpressionList -> {
                var result = voidField
                for (expr in expr.list) {
                    result = simplifyImpl(context, expr, addToBlock, graph, needsValue)
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
                val field = simplifyImpl(context, expr.value, addToBlock, graph, true)
                if (field != null) addToBlock.add(SimpleReturn(field, expr.scope, expr.origin))
                // todo else write unreachable?
                voidField
            }

            is AssignmentExpression -> {
                // todo get all stuff on the left
                val dstExpr = expr.variableName as FieldExpression
                val newValue = simplifyImpl(context, expr.newValue, addToBlock, graph, true)
                    ?: return null
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                // todo we should call the setter, if there is one
                addToBlock.add(SimpleSetField(self, dstExpr.field, newValue, expr.scope, expr.origin))
                voidField
            }
            is CompareOp -> {
                val base = simplifyImpl(context, expr.value, addToBlock, graph, true) ?: return null
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleCompare(dst, base, expr.type, expr.scope, expr.origin))
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
                simplifyCall(context, expr, addToBlock, graph, expr.base, expr.valueParameters, method)
            }
            is CallExpression -> {
                val method = expr.resolveMethod(context)
                simplifyCall(context, expr, addToBlock, graph, expr.base, expr.valueParameters, method)
            }
            is SpecialValueExpression -> {
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleSpecialValue(dst, expr))
                dst
            }
            is UnresolvedFieldExpression -> {
                val field = expr.resolveField(context)
                    ?: throw IllegalStateException("Failed to resolve field $expr")
                val self: SimpleField? =
                    null // todo if field.selfType == null, nothing, else find the respective "this" from the scope
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                dst
            }
            is NumberExpression -> {
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleNumber(dst, expr))
                dst
            }
            is StringExpression -> {
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleString(dst, expr))
                dst
            }
            is IfElseBranch -> simplifyBranch(context, expr, addToBlock, graph, needsValue)
            is DotExpression -> {
                val field = expr.resolveField(context)
                    ?: TODO("Implement dot-expression on call or similar")
                val self = simplifyImpl(context, expr.left, addToBlock, graph, true)
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleGetField(dst, self, field.resolved, expr.scope, expr.origin))
                dst
            }
            // todo PrefixExpression should be solved by calling functions, too
            is PostfixExpression -> {
                // todo this should be solved by calling functions; there is no reason
                //  for having a separate class

                val base = simplifyImpl(context, expr.base, addToBlock, graph, true)
                // todo this calls .inc and .dec, and reassigns the value -> this must be a field
                //  or something with get() and set() logic...
                when (expr.type) {
                    PostfixType.INCREMENT -> {

                    }
                    PostfixType.DECREMENT -> {

                    }
                }
                TODO("Implement post++ and post--")
            }
            else -> TODO("Simplify value ${expr.javaClass.simpleName}: $expr")
        }
    }

    private fun simplifyBranch(
        context: ResolutionContext,
        expr: IfElseBranch,
        addToBlock: SimpleBlock,
        graph: SimpleGraph,
        needsValue: Boolean,
    ): SimpleField? {
        val dst = addToBlock.field(expr)
        val condition = simplifyImpl(context, expr.condition, addToBlock, graph, true)
            ?: throw IllegalStateException("Condition for if didn't return a field")
        // todo if not condition, create unreachable...
        val ifBlock = graph.addBlock()
        val elseBlock = graph.addBlock()
        val ifValue = simplifyImpl(context.withCodeScope(expr.ifBranch.scope), expr, ifBlock, graph, true)
        val elseValue = simplifyImpl(context.withCodeScope(expr.elseBranch!!.scope), expr, elseBlock, graph, true)
        addToBlock.add(SimpleBranch(condition, ifBlock, elseBlock, expr.scope, expr.origin))
        return if (ifValue != null && elseValue != null) {
            addToBlock.add(SimpleMerge(dst, ifBlock, ifValue, elseBlock, elseValue, expr))
            dst
        } else ifValue ?: elseValue
    }

    private fun simplifyCall(
        context: ResolutionContext,
        expr: Expression,
        addToBlock: SimpleBlock,
        graph: SimpleGraph,

        exprBase: Expression,
        exprValueParameters: List<NamedParameter>,

        method0: ResolvedMember<*>,
    ): SimpleField? {
        when (val method = method0.resolved) {
            is Method -> {
                val base = simplifyImpl(context, exprBase, addToBlock, graph, true)
                    ?: return null
                val params =
                    reorderParameters(exprValueParameters, method.valueParameters, expr.scope, expr.origin)
                        .map { parameter ->
                            simplifyImpl(context, parameter, addToBlock, graph, true)
                                ?: return null
                        }
                // then execute it
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleCall(dst, method, base, params, expr.scope, expr.origin))
                return dst
            }
            is Constructor -> {
                // base is a type
                val params =
                    reorderParameters(
                        exprValueParameters,
                        method.valueParameters,
                        expr.scope, expr.origin
                    ).map { parameter ->
                        simplifyImpl(context, parameter, addToBlock, graph, true)
                            ?: return null
                    }
                // then execute it
                val dst = addToBlock.field(expr)
                addToBlock.add(SimpleConstructor(dst, method, params, expr.scope, expr.origin))
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