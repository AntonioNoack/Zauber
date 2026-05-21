package me.anno.zauber.ast.simple.fields

import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleGetLocalField(
    dst: SimpleField,
    val field: LocalField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun execute(): BlockReturn? {
        // cannot crash at runtime (if ASTSimplified correctly) -> past-path
        val call = runtime.getCall()
        val value = call.localFields[field.id]
            ?: throw IllegalStateException("Missing local field #${field.id}")
        call.setSimple(dst, value)
        return null
    }

    override fun eval(): BlockReturn {
        val value = runtime.getCall().localFields[field.id]
            ?: throw IllegalStateException("Missing local field #${field.id}")
        return BlockReturn(ReturnType.VALUE, value)
    }

    override fun toString(): String {
        return "$dst = local#${field.id}"
    }
}