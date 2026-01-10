package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

class SimpleDeclaration(val type: Type, val name: String, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "$type $name;"
    }

    override fun execute(runtime: Runtime) {
        // todo define field in runtime as null/0?
    }
}