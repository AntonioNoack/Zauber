package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.impl.ClassType

class SimpleGetField(
    dst: SimpleField,
    val self: SimpleField,
    val field: Field,
    val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleGetField::class)
    }

    init {
        check(specialization.scope == field.fieldScope)
        if (self.type is ClassType) {
            val selfScope = self.type.clazz
            val fieldScope = field.ownerScope
            if (selfScope.isInnerClassOf(fieldScope) ||
                fieldScope.isInnerClassOf(selfScope)
            ) {
                throw IllegalStateException("Cannot get $field from $self")
            }
        }
        check(!field.ownerScope.isInterface()) {
            "Cannot just get field of an interface, must use getter, $field"
        }
        check(!field.isBackingField()) {
            "Cannot get 'backing' field $field in ${field.ownerScope}, needs to use direct access"
        }
    }

    override fun toString(): String {
        return "$dst = $self?[${field.selfType ?: field.ownerScope.pathStr}].${field.name}"
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val self = runtime[self]
        LOGGER.info("[SELF] ${this.self} -> $self")
        if (runtime.isNull(self)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }
        LOGGER.info("[GET] $self.${field.name} (${field.ownerScope})")
        val value = self[field]
        return BlockReturn(ReturnType.VALUE, value)
    }
}