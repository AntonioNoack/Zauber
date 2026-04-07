package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleGetObject(
    dst: SimpleField,
    val objectScope: Scope,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    init {
        objectScope.scope
        if (!objectScope.isObjectLike() && objectScope.scopeType != null) {
            throw IllegalStateException("Cannot get object of $objectScope, ${objectScope.scopeType}")
        }
    }

    override fun toString(): String {
        return "$dst = Object[$objectScope]"
    }

    override fun eval(): BlockReturn {
        val value = runtime.getObjectInstance(objectScope.typeWithArgs)
        return BlockReturn(ReturnType.VALUE, value)
    }
}