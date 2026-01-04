package me.anno.zauber.ast.rich

import me.anno.zauber.types.Type

class Annotation(val path: Type, val params: List<NamedParameter>) {
    override fun toString(): String {
        return "@$path(${params.joinToString(", ")})"
    }
}