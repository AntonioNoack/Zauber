package me.anno.zauber.generation.java

import me.anno.zauber.ast.rich.InnerSuperCall
import me.anno.zauber.ast.rich.InnerSuperCallTarget
import me.anno.zauber.ast.rich.expression.DotExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.MemberNameExpression
import me.anno.zauber.ast.rich.expression.UnresolvedFieldExpression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.typeresolution.ResolutionContext

object JavaExpressionWriter {

    private val builder = JavaSourceGenerator.builder

    fun appendSuperCall(context: ResolutionContext, superCall: InnerSuperCall) {
        builder.append(if (superCall.target == InnerSuperCallTarget.THIS) "this(" else "super(")
        for (parameter in superCall.valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendExpression(context, parameter.value)
        }
        builder.append(");")
        JavaSourceGenerator.nextLine()
    }

    fun appendExpression(context: ResolutionContext, expression: Expression) {
        when (expression) {
            is DotExpression -> {
                appendExpression(context, expression.left)
                builder.append('.')
                appendExpression(context, expression.right)
            }
            is UnresolvedFieldExpression -> {
                builder.append(expression.name)
            }
            is MemberNameExpression -> {
                builder.append(expression.name)
            }
            is NumberExpression -> {
                builder.append(expression.value)
            }
            is StringExpression -> {
                // todo do we need the quotes?
                builder.append('"').append(expression.value).append('"')
            }
            else -> builder.append(expression).append("/* ")
                .append(expression.javaClass.simpleName).append(" */")
        }
    }

}