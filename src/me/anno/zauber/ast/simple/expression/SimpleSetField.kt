package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope

class SimpleSetField(
    val self: SimpleField,
    val field: Field,
    val value: SimpleField,
    scope: Scope, origin: Int
) : SimpleInstruction(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleSetField::class)
    }

    init {
        if (field.classScope.isInterface()) {
            throw IllegalStateException("Cannot just set field of an interface, must use getter, $field")
        }
    }

    override fun toString(): String {
        return "$self?[${field.selfType}].${field.name} = $value"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        val selfInstance = runtime[self]
        val value = runtime[value]
        LOGGER.info("[SET] $selfInstance.${field.name} = $value")
        runtime[selfInstance, field] = value.cloneIfValue()
        return null
    }
}