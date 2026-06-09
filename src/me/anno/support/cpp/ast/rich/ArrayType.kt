package me.anno.support.cpp.ast.rich

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.interpreting.ConstExpr.evaluateExpression
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ModifierType

class ArrayType(type: Type, val sizes: List<Int>) : ModifierType(type) {

    override fun toStringImpl(depth: Int): String = "$type[$sizes]"
    override fun withType(type: Type): Type = ArrayType(type, sizes)
    override fun equals(other: Any?): Boolean {
        return other is ArrayType &&
                type == other.type &&
                sizes == other.sizes
    }

    override fun hashCode(): Int {
        return type.hashCode() * 31 + sizes.hashCode()
    }

    companion object {
        fun createArrayType(type: Type, sizeExpr: Expression): ArrayType {
            val size = evaluateExpression(sizeExpr, 0, Types.Int).castToInt()
            check(size >= 0) {
                "Invalid size expression: $size at ${resolveOrigin(sizeExpr.origin)}"
            }
            return if (type is ArrayType) {
                ArrayType(type.type, type.sizes + size)
            } else ArrayType(type, listOf(size))
        }
    }
}