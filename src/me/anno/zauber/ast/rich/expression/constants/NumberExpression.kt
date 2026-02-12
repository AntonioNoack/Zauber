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
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.UIntType
import me.anno.zauber.types.Types.ULongType
import me.anno.zauber.types.impl.ClassType
import kotlin.math.pow

// todo the "true" type of this also could be "ComptimeValue", because it is :)
class NumberExpression(val value: String, scope: Scope, origin: Int) : Expression(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(NumberExpression::class)

        fun parseHexFloat(value: String): Double {
            var result = 0.0
            var i = 0
            while (i < value.length) {
                result = result * 10.0 + when (val c = value[i++]) {
                    in '0'..'9' -> c - '0'
                    in 'A'..'F' -> c - 'A' + 10
                    in 'a'..'f' -> c - 'a' + 10
                    else -> {
                        i--
                        break
                    }
                }
            }
            if (i < value.length && value[i] == '.') {
                i++
                val factor = 1.0 / 16.0
                var exponent = 1.0
                while (i < value.length) {
                    exponent *= factor
                    result += exponent * when (val c = value[i++]) {
                        in '0'..'9' -> c - '0'
                        in 'A'..'F' -> c - 'A' + 10
                        in 'a'..'f' -> c - 'a' + 10
                        else -> {
                            i--
                            break
                        }
                    }
                }
            }
            if (i + 1 < value.length && value[i] in "pP") {
                i++
                val isNegative = value[i] == '-'
                if (isNegative || value[i] == '+') i++
                var exponent = 0
                while (i < value.length) {
                    exponent = exponent * 10 + when (val c = value[i++]) {
                        in '0'..'9' -> c - '0'
                        else -> {
                            i--
                            break
                        }
                    }
                }
                if (isNegative) exponent = -exponent
                result *= 2.0.pow(exponent)
            }
            if (i < value.length && value[i] in "fFdD") i++
            check(i == value.length)
            return result
        }

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
        val extraLength = value.count { it == '_' }
        return if (value.length <= 9 + extraLength &&
            value.replace("_", "")
                .toIntOrNull() != null
        ) IntType else LongType
    }

    private fun resolveHexIntType(): ClassType {
        val extraLength = 2 + value.count { it == '_' }
        return if (value.length <= 8 + extraLength &&
            value.substring(2)
                .replace("_", "")
                .toIntOrNull(16) != null
        ) IntType else LongType
    }

    private fun resolveBinIntType(): ClassType {
        val extraLength = 2 + value.count { it == '_' }
        return if (value.length <= 32 + extraLength &&
            value.substring(2)
                .replace("_", "")
                .toIntOrNull(2) != null
        ) IntType else LongType
    }

    private fun typeBySuffix(): ClassType? {
        val maybeIsFloat = !value.startsWith("0x", true) || value.contains("p", true)
        return when {
            value.endsWith("ul", true) -> ULongType
            value.endsWith("u", true) -> UIntType
            value.endsWith("l", true) -> LongType
            value.endsWith("h", true) -> HalfType
            maybeIsFloat && value.endsWith("f", true) -> FloatType
            maybeIsFloat && value.endsWith("d", true) -> DoubleType
            value.startsWith("0x", true) &&
                    ('.' in value || value.contains("pP", true)) -> DoubleType
            else -> null
        }
    }

    override fun toStringImpl(depth: Int): String {
        return "NumberExpr($value)"
    }

    override fun resolveReturnType(context: ResolutionContext): Type {
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

            if (str.startsWith("0x")) {
                str = str.substring(2).toInt(16).toString()
            } else {
                if (str.isNotEmpty() && str.last() in "hfdHFD") {
                    str = str.substring(0, str.length - 1)
                }
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

    override fun resolveThrownType(context: ResolutionContext): Type = NothingType
    override fun resolveYieldedType(context: ResolutionContext): Type = NothingType

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}