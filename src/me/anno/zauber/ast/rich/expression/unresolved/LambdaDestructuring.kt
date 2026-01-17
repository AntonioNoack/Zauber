package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.ast.rich.Field

class LambdaDestructuring(val components: List<LambdaVariable>, syntheticField: Field) :
    LambdaVariable(null, syntheticField) {

    override fun toString(): String {
        return "(${components.joinToString(", ")})"
    }
}