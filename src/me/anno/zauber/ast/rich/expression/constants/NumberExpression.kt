package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.HalfType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.UIntType
import me.anno.zauber.types.Types.ULongType
import me.anno.zauber.types.impl.ClassType

// todo the "true" type of this also could be "ComptimeValue", because it is :)
class NumberExpression(val value: String, scope: Scope, origin: Int) : Expression(scope, origin) {

    // based on the string content, decide what type this is
    val resolvedType0 = when {
        value.startsWith("'") -> CharType
        value.startsWith("0x", true) ||
                value.startsWith("-0x", true) -> resolveIntType()
        value.endsWith('h', true) -> HalfType
        value.endsWith('f', true) -> FloatType
        value.endsWith('d', true) -> DoubleType
        // does Kotlin have numbers with binary exponent? -> no, but it might be useful...
        value.contains('.') || value.contains('e', true) -> DoubleType
        else -> resolveIntType()
    }

    private fun resolveIntType(): ClassType {
        return when {
            value.endsWith("ul", true) -> ULongType
            value.endsWith("u", true) -> UIntType
            value.endsWith("l", true) -> LongType
            // value.length <= 3 && value.toByteOrNull() != null -> ByteType
            // value.length <= 5 && value.toShortOrNull() != null -> ShortType
            value.length <= 9 && value.toIntOrNull() != null -> IntType
            else -> LongType
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "NumberExpr($value)"
    }

    override fun resolveType(context: ResolutionContext): Type {
        // todo if resolvedType is int, but context requests byte or short,
        //  and the value fits, then return that instead
        return resolvedType0
    }

    override fun clone(scope: Scope) = NumberExpression(value, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(): Boolean = false
}