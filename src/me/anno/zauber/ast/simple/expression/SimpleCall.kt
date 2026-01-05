package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleCall(
    val method: Method,
    val base: SimpleField,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin) {

    init {
        check(method.valueParameters.size == valueParameters.size)
    }

}