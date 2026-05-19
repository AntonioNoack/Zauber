package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class SimpleDeclareAndAssign(
    type: Type, field: Field,
    val self: SimpleField, // method scope
    val value: SimpleField,
    scope: Scope, origin: Long
) : SimpleDeclaration(type, field, scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleDeclareAndAssign::class)
    }

    override fun toString(): String {
        return "DECL: $type $name = $value;"
    }

    override fun execute(): BlockReturn? {
        // shall we do anything?
        val runtime = runtime
        val selfInstance = runtime[self]
        val value = runtime[value]
        LOGGER.info("[DECL] $selfInstance.${field.name} (${field.ownerScope}) = $value")
        runtime[selfInstance, field] = value.cloneIfValue()
        return null
    }
}