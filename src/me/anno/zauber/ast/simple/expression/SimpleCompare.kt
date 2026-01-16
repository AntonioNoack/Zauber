package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Scope

class SimpleCompare(
    dst: SimpleField, val left: SimpleField, val right: SimpleField, val type: CompareType,
    val callable: ResolvedMethod, scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${type.symbol} $right"
    }

    override fun eval(runtime: Runtime): Instance {
        throw NotImplementedError("Should be resolved")
    }
}