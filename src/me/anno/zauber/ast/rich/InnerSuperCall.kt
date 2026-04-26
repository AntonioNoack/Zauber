package me.anno.zauber.ast.rich

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>,
    val origin: Int
) {
    override fun toString(): String {
        return "$target(${valueParameters.joinToString { it.toString() }})"
    }
}