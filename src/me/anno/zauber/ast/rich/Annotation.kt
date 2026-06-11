package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.parameter.NamedParameter
import me.anno.zauber.interpreting.ConstExpr
import me.anno.zauber.types.Type

class Annotation(
    var type: Type, val params: List<NamedParameter>,
    val scope: String /* e.g. @file:Suppress() -> "file" */
) {

    // todo this shall be a constructor call, so resolve it as such

    val params1 by lazy {
        // todo this could be specialization dependent!
        params.map {
            ConstExpr.evaluateExpression(it.value, Flags.CONSTEXPR, null)
        }
    }

    override fun toString(): String {
        return "@$type(${params.joinToString(", ")})"
    }
}