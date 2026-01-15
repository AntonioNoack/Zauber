package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.unresolved.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

@Deprecated("Only simplify resolved types")
class SimpleCompare(dst: SimpleField, val left: SimpleField, val right: SimpleField, val type: CompareType, scope: Scope, origin: Int) :
    SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${type.symbol} 0"
    }

    override fun eval(runtime: Runtime): Instance {
        throw NotImplementedError("Should be resolved")
    }
}