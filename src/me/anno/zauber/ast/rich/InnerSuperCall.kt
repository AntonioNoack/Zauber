package me.anno.zauber.ast.rich

enum class InnerSuperCallTarget {
    THIS,
    SUPER
}

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>
)