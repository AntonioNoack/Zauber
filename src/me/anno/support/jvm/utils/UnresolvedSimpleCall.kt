package me.anno.support.jvm.utils

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.scope.Scope

enum class CallType {
    STATIC,
    DYNAMIC,
    SPECIAL,
    INTERFACE
}

class UnresolvedSimpleCall(
    dst: SimpleField,
    val type: CallType, val owner: String, val name: String, val descriptor: String,
    val self: SimpleField,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun eval(): BlockReturn {
        TODO("Not yet implemented")
    }

}