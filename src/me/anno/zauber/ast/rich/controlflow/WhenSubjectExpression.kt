package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ASTBuilderBase
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Keywords
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.LambdaType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import sun.reflect.generics.reflectiveObjects.NotImplementedException

private val LOGGER = LogManager.getLogger("WhenSubjectExpression")

class SubjectWhenCase(val conditions: List<SubjectCondition>?, val conditionScope: Scope, val body: Expression) {

    override fun toString(): String {
        return "${conditions?.joinToString(", ") ?: "else"} -> { $body }"
    }

    fun toCondition(astBuilder: ASTBuilderBase, subject: Expression): Expression {
        val scope = conditionScope
        val expressions = conditions!!.map { condition -> condition.toExpression(astBuilder, subject, scope) }
        return expressions.reduce { a, b -> shortcutExpression(a, ShortcutOperator.OR, b, scope, a.origin) }
    }
}

fun lambdaTypeToClassType(lambdaType: LambdaType): ClassType {
    val base = root.getOrPut("Function${lambdaType.parameters.size}", ScopeType.INTERFACE)
    return ClassType(base, lambdaType.parameters.map { it.type })
}

fun storeSubject(
    scope: Scope,
    subject: Expression,
): FieldExpression {
    val origin = subject.origin
    val subjectName = scope.generateName("subject")
    val value = if (subject is AssignmentExpression) subject.newValue else subject
    val field = Field(
        scope, scope.typeWithoutArgs, false, null, subjectName,
        null, value, Keywords.NONE, origin
    )
    return FieldExpression(field, scope, origin)
}

fun ASTBuilderBase.whenSubjectToIfElseChain(
    scope: Scope,
    subject: Expression,
    cases: List<SubjectWhenCase>
): Expression {
    val origin = subject.origin
    val subjectExpr = storeSubject(scope, subject)
    val assignment = AssignmentExpression(subjectExpr, subject)
    val cases = cases.map { case ->
        val condition =
            if (case.conditions != null) {
                case.toCondition(this, subjectExpr)
            } else null // else-case
        // if all conditions are 'is X',
        //  then join them together, and insert a field with more specific type...
        if (case.conditions != null && case.conditions.all { it.subjectConditionType == SubjectConditionType.INSTANCEOF }) {
            val fieldName = when (subject) {
                is AssignmentExpression -> when (val name = subject.variableName) {
                    is MemberNameExpression -> name.name
                    is FieldExpression -> name.field.name /* todo in this case, we can reuse the field, I think */
                    else -> throw NotImplementedException()
                }
                is MemberNameExpression -> subject.name
                else -> null
            }
            if (fieldName != null) {
                val caseScope = case.body.scope
                val jointType = unionTypes(case.conditions.map { it.type!! })
                // todo this more-specific field is only valid until fieldName is assigned, again, then we have to use unionType
                // todo this is also only valid, if no other thread/function could write to the field
                Field(
                    caseScope, null, false, null,
                    fieldName, jointType, null, Keywords.NONE, origin
                )
            }
        }
        if (condition != null) {
            check(condition.scope == case.conditionScope) {
                "Expected condition to have ${case.conditionScope}, but got ${condition.scope}, " +
                        "conditions: ${case.conditions}"
            }
        }
        if (false) {
            LOGGER.info("new-case:")
            LOGGER.info("  condition: ${condition?.scope?.pathStr}")
            LOGGER.info("  body: ${case.body.scope.pathStr}")
        }
        WhenCase(condition, case.body)
    }

    val whenExpression = whenBranchToIfElseChain(cases, subject.scope, origin)
    return ExpressionList(listOf(assignment, whenExpression), subject.scope, origin)
}