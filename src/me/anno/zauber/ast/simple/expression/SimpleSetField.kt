package me.anno.zauber.ast.simple.expression

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

class SimpleSetField(
    override val self: SimpleField,
    override val field: Field,
    val value: SimpleField,
    override val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleInstruction(scope, origin), SimpleGetOrSetField {

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleSetField::class)
    }

    init {
        check(specialization.scope == field.fieldScope) {
            "Expected specialization to belong to $field (${field.fieldScope}), but got ${specialization.scope}"
        }
        check(!field.ownerScope.isInterface()) {
            "Cannot just set field of an interface, must use getter, $field"
        }
        check(!field.isBackingField()) {
            "Cannot get 'backing' field $field in ${field.ownerScope}, needs to use direct access"
        }
    }

    override fun toString(): String {
        return "$self?[${style(field.selfType.toString(), StringStyles.LINK)}]." +
                "${style(field.name, StringStyles.GREEN)} = $value"
    }

    override fun withField(field: Field): SimpleInstruction {
        val spec = Specialization(field.fieldScope, specialization.typeParameters)
        return SimpleSetField(self, field, value, spec, scope, origin)
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