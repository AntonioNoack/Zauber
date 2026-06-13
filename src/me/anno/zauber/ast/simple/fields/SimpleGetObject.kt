package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles.bold
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType

class SimpleGetObject(
    dst: SimpleField,
    val objectScope: Scope,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    init {
        objectScope[ScopeInitType.AFTER_DISCOVERY]
        if (!objectScope.isObjectLike() && objectScope.scopeType != null) {
            error("Cannot get object of $objectScope, ${objectScope.scopeType}")
        }
    }

    override fun toString(): String {
        return "$dst = ${bold("Object")}[$objectScope]"
    }

    override fun hasInput(field: SimpleField): Boolean = false

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime.getObjectInstance(objectScope)
        return null
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleGetObject(
            src.cloned(this.dst, dst),
            objectScope, scope, origin
        )
    }

}