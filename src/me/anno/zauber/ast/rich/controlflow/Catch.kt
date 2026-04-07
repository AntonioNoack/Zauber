package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.Expression

class Catch(val parameter: Parameter, val body: Expression, val origin: Int) {
    override fun toString(): String {
        return "catch(${parameter.name}: ${parameter.type}) { $body }"
    }
}