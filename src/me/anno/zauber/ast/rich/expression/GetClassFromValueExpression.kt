package me.anno.zauber.ast.rich.expression

fun getClassFromValueExpression(value: Expression, origin: Int): Expression {
    return NamedCallExpression(
        value, "getZClass",
        emptyList(), emptyList(), value.scope, origin
    )
}