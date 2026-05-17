package me.anno.zauber.ast.rich.parameter

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>,
    val origin: Long
) {
    override fun toString(): String {
        return "$target(${valueParameters.joinToString { it.toString() }})"
    }
}