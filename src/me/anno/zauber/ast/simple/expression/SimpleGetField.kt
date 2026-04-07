package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope

class SimpleGetField(
    dst: SimpleField,
    val self: SimpleField,
    val field: Field,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleGetField::class)
    }

    init {
        if (field.classScope.isInterface()) {
            throw IllegalStateException("Cannot just get field of an interface, must use getter, $field")
        }
    }

    override fun toString(): String {
        return "$dst = $self?[${field.selfType}].${field.name}"
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val self = runtime[self]
        LOGGER.info("[SELF] ${this.self} -> $self")
        if (runtime.isNull(self)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }
        LOGGER.info("[GET] $self.${field.name}")
        val value = self[field]
        return BlockReturn(ReturnType.VALUE, value)
    }
}