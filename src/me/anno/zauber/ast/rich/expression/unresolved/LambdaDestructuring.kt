package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.member.Field

class LambdaDestructuring(val components: List<LambdaVariable>, syntheticField: Field, origin: Long) :
    LambdaVariable(null, syntheticField, origin) {

    override fun toString(): String {
        return "(${components.joinToString(", ")})"
    }
}