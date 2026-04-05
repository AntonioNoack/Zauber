package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.TypeUtils.canInstanceBeBoth

class SimpleInstanceOf private constructor(
    dst: SimpleField,
    val value: SimpleField,
    val type: Type,
    scope: Scope, origin: Int
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $value is $type"
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val instance = runtime[value, this]
        val givenType = instance.type
        val expectedType = runtime.getClass(type)
        val value = runtime.getBool(givenType.isSubTypeOf(expectedType))
        return BlockReturn(ReturnType.VALUE, value)
    }

    companion object {
        private val LOGGER = LogManager.getLogger(SimpleInstanceOf::class)

        fun createSimpleInstanceOf(
            dst: SimpleField,
            value: SimpleField,
            type: Type,
            scope: Scope,
            origin: Int
        ): SimpleAssignment {
            val valueType = value.type
            if (!canInstanceBeBoth(valueType, type)) {
                // todo add warning about this
                LOGGER.warn("Instance cannot be $valueType and $type at once, ${resolveOrigin(origin)}")
                return SimpleSpecialValue(dst, SpecialValue.FALSE, scope, origin)
            }
            if (isSubTypeOf(expectedType = type, actualType = valueType)) {
                // todo add warning about this
                LOGGER.warn("Instance of $valueType is always a $type, ${resolveOrigin(origin)}")
                return SimpleSpecialValue(dst, SpecialValue.TRUE, scope, origin)
            }
            return SimpleInstanceOf(dst, value, type, scope, origin)
        }
    }
}