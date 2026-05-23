package me.anno.zauber.ast.simple.expression

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.impl.ClassType

class SimpleGetField(
    dst: SimpleField,
    override val self: SimpleField,
    override val field: Field,
    override val specialization: Specialization,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin), SimpleGetOrSetField {

    init {
        check(specialization.scope == field.fieldScope) {
            "Field must match specialization scope, ${specialization.scope} != ${field.fieldScope}"
        }

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
        return "$dst = $self?[${style(field.selfType?.toString() ?: field.ownerScope.pathStr, StringStyles.LINK)}]." +
                style(field.name, StringStyles.GREEN)
    }

    override fun withField(field: Field): SimpleInstruction {
        val spec = Specialization(field.fieldScope, specialization.typeParameters)
        return SimpleGetField(dst, self, field, spec, scope, origin)
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        val self = runtime[self]
        runtime[dst] = self[field]
        return null
    }
}