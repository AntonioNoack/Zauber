package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.ast.rich.parser.ASTBuilderBase

enum class InplaceModifyType(val symbol: String, val methodName: String) {
    INCREMENT("++", "inc"),
    DECREMENT("--", "dec")
}

fun ASTBuilderBase.createPrefixExpression(type: InplaceModifyType, origin: Long, base: Expression): Expression {
    return createPrePostfixExpression(base, type, origin, false)
}

fun ASTBuilderBase.createPostfixExpression(base: Expression, type: InplaceModifyType, origin: Long): Expression {
    return createPrePostfixExpression(base, type, origin, true)
}

private fun ASTBuilderBase.createPrePostfixExpression(
    base0: Expression, type: InplaceModifyType, origin: Long,
    returnBeforeChange: Boolean
): Expression {
    val scope = base0.scope
    val getterSetter = splitGetterSetter(base0)
    val instr = ArrayList<Expression>()
    if (returnBeforeChange) {
        val tmpBefore = scope.createImmutableField(getterSetter.beforeChange, "postfix1", origin)
        val tmpBeforeExpr = FieldExpression(tmpBefore, scope, origin)
        instr.add(AssignmentExpression(tmpBeforeExpr, getterSetter.beforeChange))
        val afterChange = NamedCallExpression(
            tmpBeforeExpr, type.methodName,
            nameAsImport(type.methodName), scope, origin
        )
        instr.add(getterSetter.createSetter(afterChange))
        instr.add(tmpBeforeExpr)
    } else {
        val afterChange = NamedCallExpression(
            getterSetter.beforeChange, type.methodName,
            nameAsImport(type.methodName), scope, origin
        )
        val tmpAfter = scope.createImmutableField(afterChange, "postfix2", origin)
        val tmpAfterExpr = FieldExpression(tmpAfter, scope, origin)
        instr.add(AssignmentExpression(tmpAfterExpr, afterChange))
        instr.add(getterSetter.createSetter(tmpAfterExpr))
        instr.add(tmpAfterExpr)
    }
    return ExpressionList(instr, scope, origin)
}

private abstract class GetterSetter(val beforeChange: Expression) {
    abstract fun createSetter(newValue: Expression): Expression
}

private fun splitGetterSetter(base: Expression): GetterSetter {
    return object : GetterSetter(base) {
        override fun createSetter(newValue: Expression): Expression {
            return AssignmentExpression(base, newValue)
        }
    }
}