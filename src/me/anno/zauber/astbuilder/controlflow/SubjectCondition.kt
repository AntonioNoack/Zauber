package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.ASTBuilder
import me.anno.zauber.astbuilder.expression.*
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
                PrefixExpression(PrefixType.NOT, subject.origin, buildIsExpr(this, subject, newScope))
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
        return InstanceOfCheckExpr(subject, type, false, newScope, subject.origin)
    }

    override fun toString(): String {
        val prefix = subjectConditionType.symbol
        return "$prefix ${value ?: type}${if (extraCondition != null) " if ($extraCondition)" else ""}"
    }
}
