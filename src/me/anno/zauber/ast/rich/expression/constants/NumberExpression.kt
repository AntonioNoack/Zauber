package me.anno.zauber.ast.rich.expression.constants

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import kotlin.math.min
import kotlin.math.pow

// todo the "true" type of this also could be "ComptimeValue", because it is :)
class NumberExpression(val value: String, scope: Scope, origin: Long) : Expression(scope, origin) {

    companion object {

        private val LOGGER = LogManager.getLogger(NumberExpression::class)

        private fun parseDigit(c: Char): Int {
            return when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'F' -> c - 'A' + 10
                in 'a'..'f' -> c - 'a' + 10
                else -> 1000
            }
        }

        fun parseFloat(value: String, basis: Int): Double {
            var result = 0.0
            var i = 0
            if (value.startsWith('-')) i++ // skip sign
            if (value.startsWith("0x", i, true) ||
                value.startsWith("0b", i, true)
            ) {
                check(basis != 10)
                i += 2
            }
            while (i < value.length) {
                val char = value[i++]
                if (char == '_') continue
                val digit = parseDigit(char)
                if (digit >= basis) {
                    i--
                    break
                }
                result = result * basis + digit
            }
            if (i < value.length && value[i] == '.') {
                i++ // skip period
                val factor = 1.0 / basis
                var exponent = 1.0
                while (i < value.length) {
                    exponent *= factor
                    val char = value[i++]
                    if (char == '_') continue
                    val digit = parseDigit(char)
                    if (digit >= basis) {
                        i--
                        break
                    }
                    result += exponent * digit
                }
            }
            if (i + 1 < value.length && value[i] in "pP") {
                i++ // skip symbol
                val isNegative = value[i] == '-'
                if (isNegative || value[i] == '+') i++
                var exponent = 0
                while (i < value.length) {
                    // confirm with URL, that exponent should be decimal
                    // https://docs.oracle.com/javase/specs/jls/se11/html/jls-3.html#jls-BinaryExponent
                    val char = value[i++]
                    if (char == '_') continue
                    if (char in '0'..'9') {
                        exponent = exponent * 10 + (char - '0')
                        exponent = min(exponent, 1000_000) // will be null anyway
                    } else {
                        i--
                        break
                    }
                }

                if (isNegative) exponent = -exponent
                result *= 2.0.pow(exponent)
            }

            if (i + 1 < value.length && value[i] in "eE") {
                i++ // skip symbol
                val isNegative = value[i] == '-'
                if (isNegative || value[i] == '+') i++
                var exponent = 0
                while (i < value.length) {
                    // confirm with URL, that exponent should be decimal
                    // https://docs.oracle.com/javase/specs/jls/se11/html/jls-3.html#jls-BinaryExponent
                    val char = value[i++]
                    if (char == '_') continue
                    if (char in '0'..'9') {
                        exponent = exponent * 10 + (char - '0')
                        exponent = min(exponent, 1000) // will be null anyway
                    } else {
                        i--
                        break
                    }
                }

                if (isNegative) exponent = -exponent
                result *= 10.0.pow(exponent)
            }
            if (i < value.length && value[i] in "fFdDlLuU") i++
            check(i == value.length) {
                "Missed reading float part '${value[i]}' @$i in '$value'"
            }
            return if (value.startsWith('-')) -result else +result
        }

        fun parseInt(value: String, basis: Int): Long {
            var result = 0L
            var i = 0
            val isNegative = value.startsWith('-')
            if (isNegative) i++ // skip sign
            if (value.startsWith("0x", i, true) ||
                value.startsWith("0b", i, true)
            ) {
                check(basis != 10)
                i += 2
            }
            while (i < value.length) {
                val char = value[i++]
                if (char == '_') continue
                val digit = parseDigit(char)
                if (digit >= basis) {
                    i--
                    break
                }
                // sum negatively, so we can parse min_value
                result = Math.multiplyExact(result, basis.toLong())
                result = Math.subtractExact(result, digit.toLong())
            }
            if (i < value.length && value[i] in "lLuU") i++
            check(i == value.length) {
                "Missed reading int part '${value[i]}' @$i in '$value'"
            }
            if (!isNegative && result == Long.MIN_VALUE) {
                throw ArithmeticException("long overflow")
            }
            return if (isNegative) result else -result
        }

        fun parseBasis(value: String): Int {
            return when {
                value.startsWith("0x", true) || value.startsWith("-0x", true) -> 16
                value.startsWith("0b", true) || value.startsWith("-0b", true) -> 2
                else -> 10
            }
        }
    }

    val basis = parseBasis(value)

    // based on the string content, decide what type this is
    val resolvedType0 = typeBySuffix() ?: when {
        value.startsWith("'") -> Types.Char
        basis == 16 -> resolveHexIntType()
        basis == 2 -> resolveBinIntType()
        // does Kotlin have numbers with binary exponent? -> no, but it might be useful...
        value.contains('.') ||
                value.contains('e', true) ||
                value.contains('p', true) -> Types.Double
        else -> resolveIntType()
    }

    val isFloaty = resolvedType0 == Types.Half || resolvedType0 == Types.Float || resolvedType0 == Types.Double

    // todo bug: ULong would currently overflow, although it is correct and valid
    val asFloat = parseFloat(value, basis) // should always work
    val asInt = if (isFloaty) asFloat.toLong() else parseInt(value, basis)

    private fun resolveIntType(): ClassType {
        val extraLength = value.count { it in "_-" }
        return if (value.length <= 10 + extraLength &&
            value.replace("_", "")
                .toIntOrNull() != null
        ) Types.Int else Types.Long
    }

    private fun resolveHexIntType(): ClassType {
        val extraLength = 2 + value.count { it in "_-" }
        val start = if (value.startsWith('-')) 1 else 0
        return if (value.length <= 8 + extraLength &&
            value.removeRange(start, start + 2)
                .replace("_", "")
                .toIntOrNull(16) != null
        ) Types.Int else Types.Long
    }

    private fun resolveBinIntType(): ClassType {
        val extraLength = 2 + value.count { it in "_-" }
        val start = if (value.startsWith('-')) 1 else 0
        return if (value.length <= 32 + extraLength &&
            value.removeRange(start, start + 2)
                .replace("_", "")
                .toIntOrNull(2) != null
        ) Types.Int else Types.Long
    }

    private fun typeBySuffix(): ClassType? {
        if (value.contains('p', true)) {
            return when (value.last()) {
                'f', 'F' -> Types.Float
                'h', 'H' -> Types.Half
                else -> Types.Double
            }
        }

        val maybeIsFloat = !(value.startsWith("0x", true) || value.startsWith("-0x", true))
        return when {
            value.endsWith("ul", true) -> Types.ULong
            value.endsWith("u", true) -> Types.UInt
            value.endsWith("l", true) -> Types.Long
            value.endsWith("h", true) -> Types.Half
            maybeIsFloat && value.endsWith("f", true) -> Types.Float
            maybeIsFloat && value.endsWith("d", true) -> Types.Double
            (value.startsWith("0x", true) || value.startsWith("0b", true)) &&
                    '.' in value -> Types.Double
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
        if (dataType == Types.Int && when (targetType) {
                Types.Long -> true
                Types.Byte -> checkIntLoss { it.toInt().toByte().toLong() }
                Types.Short -> checkIntLoss { it.toInt().toShort().toLong() }
                Types.Half, // todo check for loss
                Types.Float -> checkFloatLoss { it.toFloat().toDouble() }
                Types.Double -> true
                else -> false
            }
        ) return targetType
        if (dataType == Types.Half && targetType == Types.Double) {
            // todo toHalf()
            checkFloatLoss { it.toFloat().toDouble() }
            return targetType
        }
        if (dataType == Types.Float && targetType == Types.Double) {
            checkFloatLoss { it.toFloat().toDouble() }
            return targetType
        }
        // todo if resolvedType is int, but context requests byte or short,
        //  and the value fits, then return that instead
        return dataType
    }

    fun checkIntLoss(cast: (Long) -> Long): Boolean {
        val maxPrecision = asInt
        val realPrecision = cast(maxPrecision)
        if (realPrecision != maxPrecision) {
            LOGGER.warn("Losing information when casting $maxPrecision to $realPrecision")
        }
        return true
    }

    fun checkFloatLoss(cast: (Double) -> Double): Boolean {
        val maxPrecision = asFloat
        val realPrecision = cast(maxPrecision)
        if (realPrecision.toString() != maxPrecision.toString()) {
            LOGGER.warn("Losing information when casting $maxPrecision to $realPrecision")
        }
        return true
    }

    override fun clone(scope: Scope) = NumberExpression(value, scope, origin)

    override fun resolveThrownType(context: ResolutionContext): Type = Types.Nothing
    override fun resolveYieldedType(context: ResolutionContext): Type = Types.Nothing

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false
    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = true
    override fun forEachExpression(callback: (Expression) -> Unit) {}
}