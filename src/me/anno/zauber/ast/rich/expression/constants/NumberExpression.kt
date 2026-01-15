package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.HalfType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.UIntType
import me.anno.zauber.types.Types.ULongType
import me.anno.zauber.types.impl.ClassType

// todo the "true" type of this also could be "ComptimeValue", because it is :)
class NumberExpression(val value: String, scope: Scope, origin: Int) : Expression(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(NumberExpression::class)
    }

    // based on the string content, decide what type this is
    val resolvedType0 = typeBySuffix() ?: when {
        value.startsWith("'") -> CharType
        value.startsWith("0x", true) ||
                value.startsWith("-0x", true) -> resolveHexIntType()
        value.startsWith("0b", true) ||
                value.startsWith("-0b", true) -> resolveBinIntType()
        // does Kotlin have numbers with binary exponent? -> no, but it might be useful...
        value.contains('.') || value.contains('e', true) -> DoubleType
        else -> resolveIntType()
    }

    private fun resolveIntType(): ClassType {
        return if (value.length <= 9 && value.toIntOrNull() != null) IntType else LongType
    }

    private fun resolveHexIntType(): ClassType {
        return if (value.length <= 8 + 2 && value.substring(2).toIntOrNull(16) != null) IntType else LongType
    }

    private fun resolveBinIntType(): ClassType {
        return if (value.length <= 32 + 2 && value.substring(2).toIntOrNull(2) != null) IntType else LongType
    }

    private fun typeBySuffix(): ClassType? {
        return when {
            value.endsWith("ul", true) -> ULongType
            value.endsWith("u", true) -> UIntType
            value.endsWith("l", true) -> LongType
            value.endsWith("h", true) -> HalfType
            value.endsWith("f", true) -> FloatType
            value.endsWith("d", true) -> DoubleType
            else -> null
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "NumberExpr($value)"
    }

    override fun resolveType(context: ResolutionContext): Type {
        val dataType = resolvedType0
        val targetType = context.targetType ?: return dataType
        if (targetType == dataType) return targetType
        // todo if targetType is nullable, remove that type
        if (dataType == IntType && when (targetType) {
                LongType -> true
                ByteType -> checkLoss { it.toInt().toByte() }
                ShortType -> checkLoss { it.toInt().toShort() }
                HalfType, // todo check for loss
                FloatType -> checkLoss { it.toDouble().toFloat() }
                DoubleType -> true
                else -> false
            }
        ) return targetType
        if (dataType == HalfType && targetType == DoubleType) {
            // todo toHalf()
            checkLoss { it.toDouble().toFloat() }
            return targetType
        }
        if (dataType == FloatType && targetType == DoubleType) {
            checkLoss { it.toDouble().toFloat() }
            return targetType
        }
        // todo if resolvedType is int, but context requests byte or short,
        //  and the value fits, then return that instead
        return dataType
    }

    fun checkLoss(cast: (String) -> Any): Boolean {
        try {
            var str = value
            if (str.endsWith("l", true)) str = str.substring(0, str.length - 1)
            if (str.endsWith("u", true)) str = str.substring(0, str.length - 1)
            if (str.endsWith("h", true) || str.endsWith("f", true) || str.endsWith("d", true))
                str = str.substring(0, str.length - 1)

            if (str.startsWith("0x")) {
                str = str.substring(2).toInt(16).toString()
            }
            val samy = cast(str).toString()
            if (samy != str) {
                LOGGER.warn("Losing information when casting $str to $samy")
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed checkLoss: ${e.message ?: e}")
        }
        return true
    }

    override fun clone(scope: Scope) = NumberExpression(value, scope, origin)

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
}