package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class SimpleCompare(
    dst: SimpleField,
    val left: SimpleField, val right: SimpleField, val type: CompareType,
    val numberType: Type, // must be a native number
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin) {

    override fun toString(): String {
        return "$dst = $left ${type.symbol} $right"
    }

    override fun execute(): BlockReturn? {
        val runtime = runtime
        val left = runtime[left]
        val right = runtime[right]

        val asInt = when (numberType) {

            Types.Byte -> left.castToByte().compareTo(right.castToByte())
            Types.UByte -> left.castToUByte().compareTo(right.castToUByte())

            Types.Short -> left.castToShort().compareTo(right.castToShort())
            Types.UShort -> left.castToUShort().compareTo(right.castToUShort())
            Types.Char -> left.castToChar().compareTo(right.castToChar())

            Types.Int -> left.castToInt().compareTo(right.castToInt())
            Types.UInt -> left.castToUInt().compareTo(right.castToUInt())

            Types.Long -> left.castToLong().compareTo(right.castToLong())
            Types.ULong -> left.castToULong().compareTo(right.castToULong())

            Types.Half -> left.castToHalf().compareTo(right.castToHalf())
            Types.Float -> left.castToFloat().compareTo(right.castToFloat())
            Types.Double -> left.castToDouble().compareTo(right.castToDouble())

            else -> throw NotImplementedError("Compare $numberType")
        }

        val asBool = type.eval(asInt)
        runtime[dst] = runtime.getBool(asBool)
        return null
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleCompare(
            src.cloned(this.dst, dst),
            src.cloned(left, dst),
            src.cloned(right, dst),
            type, numberType, scope, origin
        )
    }
}