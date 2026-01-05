package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.ASTBuilder
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.ast.rich.expression.binaryOp
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.LambdaType

class SubjectCondition(
    val value: Expression?, val type: Type?,
    val subjectConditionType: SubjectConditionType,
    val extraCondition: Expression?
) {
    fun toExpression(astBuilder: ASTBuilder, subject: Expression, newScope: Scope): Expression {
        return when (subjectConditionType) {
            SubjectConditionType.EQUALS ->
                astBuilder.binaryOp(newScope, subject, "==", value!!)
            SubjectConditionType.INSTANCEOF ->
                buildIsExpr(this, subject, newScope)
            SubjectConditionType.NOT_INSTANCEOF ->
                buildIsExpr(this, subject, newScope).not()
            SubjectConditionType.CONTAINS, SubjectConditionType.NOT_CONTAINS ->
                astBuilder.binaryOp(newScope, subject, subjectConditionType.symbol, value!!)
        }
    }

    private fun buildIsExpr(expr: SubjectCondition, subject: Expression, newScope: Scope): Expression {
        val type = when (expr.type) {
            is ClassType -> expr.type
            is LambdaType -> lambdaTypeToClassType(expr.type)
            else -> throw NotImplementedError("Handle is ${expr.type?.javaClass}")
        }
        return IsInstanceOfExpr(subject, type, newScope, subject.origin)
    }

    override fun toString(): String {
        val prefix = subjectConditionType.symbol
        return "$prefix ${value ?: type}${if (extraCondition != null) " if ($extraCondition)" else ""}"
    }
}
