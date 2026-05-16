package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

class SimpleSetField(
    val self: SimpleField,
    val field: Field,
    val value: SimpleField,
    val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleInstruction(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleSetField::class)
    }

    init {
        check(specialization.scope == field.fieldScope)
        check(!field.ownerScope.isInterface()) {
            "Cannot just set field of an interface, must use getter, $field"
        }
        check(!field.isBackingField()) {
            "Cannot get 'backing' field $field in ${field.ownerScope}, needs to use direct access"
        }
    }

    override fun toString(): String {
        return "$self?[${field.selfType}].${field.name} = $value"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        val selfInstance = runtime[self]
        val value = runtime[value]
        LOGGER.info("[SET] $selfInstance.${field.name} (${field.ownerScope}) = $value")
        runtime[selfInstance, field] = value.cloneIfValue()
        return null
    }
}