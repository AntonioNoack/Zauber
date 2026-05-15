package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.FieldGetterSetter.finishField
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.DotExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.scope.Scope

fun ZauberASTBuilderBase.createAssignmentInstructionsForPrimaryConstructor(
    classScope: Scope, constructorParams: List<Parameter>?,
    origin: Long
): ExpressionList {
    val result = ArrayList<Expression>()
    val scope = classScope.getOrCreatePrimaryConstructorScope()
    if (constructorParams != null) {
        for (parameter in constructorParams) {
            if (!(parameter.isVal || parameter.isVar)) continue

            val originI = parameter.origin
            val parameterField = parameter.getOrCreateField(null, Flags.NONE)
            val classField = classScope.addField(
                null, false, isMutable = parameter.isVar,
                parameter, parameter.name, parameter.type, null, Flags.SYNTHETIC, originI
            )
            val dstExpr = DotExpression(
                ThisExpression(classScope, scope, originI), null,
                FieldExpression(classField, scope, originI),
                scope, originI
            )
            val srcExpr = FieldExpression(parameterField, scope, originI)
            result.add(AssignmentExpression(dstExpr, srcExpr))

            finishField(classScope, classField)
        }
    }
    return ExpressionList(result, scope, origin)
}
