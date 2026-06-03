package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.bold
import me.anno.utils.StringStyles.style
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
        return "$dst = ${bold("Object")}[${style(objectScope.pathStr, StringStyles.LIGHT_BLUE)}]"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime[dst] = runtime.getObjectInstance(objectScope.typeWithArgs)
        return null
    }
}