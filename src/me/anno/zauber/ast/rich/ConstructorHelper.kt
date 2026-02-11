package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.DotExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.types.Scope

object ConstructorHelper {
    fun createAssignmentInstructionsForPrimaryConstructor(
        classScope: Scope, constructorParams: List<Parameter>?,
        constructorOrigin: Int
    ): ExpressionList {
        val result = ArrayList<Expression>()
        val scope = classScope.getOrCreatePrimConstructorScope()
        if (constructorParams != null) {
            for (parameter in constructorParams) {
                if (!(parameter.isVal || parameter.isVar)) continue

                val origin = parameter.origin
                val parameterField = parameter.getOrCreateField(null, Keywords.NONE)
                val classField = classScope.addField(
                    null, false, isMutable = parameter.isVar,
                    parameter, parameter.name, parameter.type, null, Keywords.SYNTHETIC, origin
                )
                val dstExpr = DotExpression(
                    ThisExpression(classScope, scope, origin), null,
                    FieldExpression(classField, scope, origin),
                    scope, origin
                )
                val srcExpr = FieldExpression(parameterField, scope, origin)
                result.add(AssignmentExpression(dstExpr, srcExpr))
            }
        }
        return ExpressionList(result, scope, constructorOrigin)
    }
}