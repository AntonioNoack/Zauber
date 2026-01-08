package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

class SimpleCheckIdentical(
    dst: SimpleField, val a: SimpleField, val b: SimpleField,
    val negated: Boolean,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $a ${if (negated) "!==" else "==="} $b"
    }

    override fun eval(runtime: Runtime): Instance {
        return runtime.getBool(runtime[a] == runtime[b])
    }

}