package me.anno.zauber.astbuilder

enum class InnerSuperCallTarget {
    THIS,
    SUPER
}

class InnerSuperCall(
    val target: InnerSuperCallTarget,
    val valueParameters: List<NamedParameter>
)