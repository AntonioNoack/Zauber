package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.ASTBuilderBase

enum class InplaceModifyType(val symbol: String, val methodName: String) {
    INCREMENT("++", "inc"),
    DECREMENT("--", "dec")
}

fun ASTBuilderBase.createPrefixExpression(type: InplaceModifyType, origin: Int, base: Expression): Expression {
    return createPrePostfixExpression(base, type, origin, false)
}

fun ASTBuilderBase.createPostfixExpression(base: Expression, type: InplaceModifyType, origin: Int): Expression {
    return createPrePostfixExpression(base, type, origin, true)
}

private fun ASTBuilderBase.createPrePostfixExpression(
    base0: Expression, type: InplaceModifyType, origin: Int,
    returnBeforeChange: Boolean
): Expression {
    val getterSetter = splitGetterSetter(base0)
    val instr = ArrayList<Expression>()
    val afterChange = NamedCallExpression(
        getterSetter.beforeChange, type.methodName,
        nameAsImport(type.methodName), base0.scope, origin
    )
    instr.add(getterSetter.createSetter(afterChange))
    instr.add(if (returnBeforeChange) getterSetter.beforeChange else afterChange)
    return ExpressionList(instr, base0.scope, origin)
}

private abstract class GetterSetter(val beforeChange: Expression) {
    abstract fun createSetter(newValue: Expression): Expression
}

private fun splitGetterSetter(base: Expression): GetterSetter {
    when (base) {
        is UnresolvedFieldExpression -> {
            return object : GetterSetter(base) {
                override fun createSetter(newValue: Expression): Expression {
                    return AssignmentExpression(base, newValue)
                }
            }
        }
        else -> TODO("Get base for ${base.javaClass.simpleName}")
    }
}