package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.InnerSuperCall
import me.anno.zauber.ast.rich.InnerSuperCallTarget
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.DotExpression
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression
import me.anno.zauber.ast.rich.expression.unresolved.UnresolvedFieldExpression
import me.anno.zauber.typeresolution.ResolutionContext

object JavaExpressionWriter {

    private val builder = JavaSourceGenerator.builder

    fun appendSuperCall(context: ResolutionContext, superCall: InnerSuperCall) {
        // todo I think this must be in one line... needs different writing, and cannot handle errors the traditional way...
        //  a) create helper functions
        //  b) implement our own constructor
        builder.append(if (superCall.target == InnerSuperCallTarget.THIS) "this(" else "super(")
        for (parameter in superCall.valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendExpression(context, parameter.value)
        }
        builder.append(");")
        JavaSourceGenerator.nextLine()
    }

    fun appendExpression(context: ResolutionContext, expr: Expression) {
        when (expr) {
            is DotExpression -> {
                appendExpression(context, expr.left)
                builder.append('.')
                appendExpression(context, expr.right)
            }
            is UnresolvedFieldExpression -> {
                builder.append(expr.name)
            }
            is MemberNameExpression -> {
                builder.append(expr.name)
            }
            is NumberExpression -> {
                builder.append(expr.value)
            }
            is SpecialValueExpression -> {
                // null, true, false are fine
                // 'this'/'super' do not exist
                builder.append(expr.type.symbol)
            }
            is StringExpression -> {
                // todo do we need the quotes?
                builder.append('"').append(expr.value).append('"')
            }
            is IfElseBranch -> {
                builder.append('(')
                appendExpression(context, expr.condition)
                builder.append(" ? ")
                appendExpression(context, expr.ifBranch)
                builder.append(" : ")
                appendExpression(context, expr.elseBranch!!)
                builder.append(')')
            }
            is CheckEqualsOp -> {
                if (expr.negated) builder.append('!')
                if (expr.byPointer || (expr.left is SpecialValueExpression) || (expr.right is SpecialValueExpression)) {
                    builder.append('(')
                    appendExpression(context, expr.left)
                    builder.append(" == ")
                    appendExpression(context, expr.right)
                    builder.append(')')
                } else {
                    builder.append("java.util.Objects.equals(")
                    appendExpression(context, expr.left)
                    builder.append(", ")
                    appendExpression(context, expr.right)
                    builder.append(')')
                }
            }
            is CallExpression -> {
                // todo properly resolve what we call on and use it...
                appendExpression(context, expr.base)
                builder.append('(')
                // todo resolve call order...
                for ((index, param) in expr.valueParameters.withIndex()) {
                    if (index > 0) builder.append(", ")
                    appendExpression(context, param.value)
                }
                builder.append(')')
            }
            else -> builder.append(expr).append("/* ")
                .append(expr.javaClass.simpleName).append(" */")
        }
    }

}