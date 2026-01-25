package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.TypeExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

// todo we need to collect any yielded types and any thrown types

object IsMethodThrowing : MethodColoring<MethodLike, Type>() {

    override fun getDependencies(method: MethodLike): List<MethodLike> {
        // todo check for any throw instructions
        val body = method.body ?: return emptyList()
        val context = ResolutionContext(method.selfType, true, method.returnType)
        val resolved = body.resolve(context)
        val result = ArrayList<MethodLike>()
        fun collect(expr: Expression?) {
            when (expr) {
                is IfElseBranch -> {
                    collect(expr.condition)
                    collect(expr.ifBranch)
                    collect(expr.elseBranch)
                }
                is ReturnExpression -> collect(expr.value)
                is ThrowExpression -> collect(expr.value)
                is YieldExpression -> collect(expr.value)

                null,
                is BreakExpression,
                is ContinueExpression,
                is TypeExpression -> {
                }
                else -> throw NotImplementedError("Is there a call in ${expr.javaClass.simpleName} $expr")
            }
        }
        collect(resolved)
        return result
    }

    override fun getSelfColor(method: MethodLike): Type {
        // todo iterate over the body
        TODO("Not yet implemented")
    }

    override fun mergeColors(
        self: Type,
        colors: List<Type>,
        isRecursive: Boolean
    ): Type {
        // recursive doesn't matter
        return unionTypes(colors, self)
    }
}