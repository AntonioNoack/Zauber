package zauber

import zauber.types.Self

interface Number : Comparable<Self> {
    operator fun plus(other: Self): Self
    operator fun minus(other: Self): Self
    operator fun times(other: Self): Self
    operator fun div(other: Self): Self
    operator fun rem(other: Self): Self

    operator fun inc(): Self
    operator fun dec(): Self

    operator fun unaryPlus(): Self = this
    fun unaryMinus(): Self
}

value class Int(val value: NativeI32) : Number,
    Comparable<Long>, Comparable<Half>, Comparable<Float>, Comparable<Double> {

    external operator fun plus(other: Int): Int
    operator fun plus(other: Long): Long = toLong() + other
    operator fun plus(other: Half): Half = toHalf() + other
    operator fun plus(other: Float): Float = toFloat() + other
    operator fun plus(other: Double): Double = toDouble() + other

    external operator fun minus(other: Int): Int
    operator fun minus(other: Long): Long = toLong() - other
    operator fun minus(other: Half): Half = toHalf() - other
    operator fun minus(other: Float): Float = toFloat() - other
    operator fun minus(other: Double): Double = toDouble() - other

    external operator fun times(other: Int): Int
    operator fun times(other: Long): Long = toLong() * other
    operator fun times(other: Half): Half = toHalf() * other
    operator fun times(other: Float): Float = toFloat() * other
    operator fun times(other: Double): Double = toDouble() * other

    external operator fun div(other: Int): Int
    operator fun div(other: Long): Long = toLong() / other
    operator fun div(other: Half): Half = toHalf() / other
    operator fun div(other: Float): Float = toFloat() / other
    operator fun div(other: Double): Double = toDouble() / other

    external operator fun rem(other: Int): Int
    operator fun rem(other: Long): Long = toLong() % other
    operator fun rem(other: Half): Half = toHalf() % other
    operator fun rem(other: Float): Float = toFloat() % other
    operator fun rem(other: Double): Double = toDouble() % other

    external infix fun or(other: Int): Int
    external infix fun and(other: Int): Int
    external infix fun xor(other: Int): Int
    external fun inv(): Int

    infix fun or(other: Long): Long = toLong() or other
    infix fun and(other: Long): Long = toLong() and other
    infix fun xor(other: Long): Long = toLong() xor other

    fun toInt(): Int = this
    external fun toLong(): Long
    external fun toHalf(): Half
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Int = this + 1
    override operator fun dec(): Int = this - 1

    external override fun unaryMinus(): Self

    infix fun until(other: Int): IntRange = IntRange(this, other)
    infix fun rangeTo(other: Int): IntRange = IntRange(this, other + 1)

    external override fun compareTo(other: Int): Int
    override fun compareTo(other: Long): Int = toLong().compareTo(other)
    override fun compareTo(other: Half): Int = compareTo(other.toDouble())
    override fun compareTo(other: Float): Int = compareTo(other.toDouble())
    external override fun compareTo(other: Double): Int
}

value class Long(val value: NativeI64) : Number,
    Comparable<Int>, Comparable<Half>, Comparable<Float>, Comparable<Double> {

    operator fun plus(other: Int): Long = plus(other.toLong())
    external operator fun plus(other: Long): Long
    operator fun plus(other: Half): Half = toHalf() + other
    operator fun plus(other: Float): Float = toFloat() + other
    operator fun plus(other: Double): Double = toDouble() + other

    operator fun minus(other: Int): Long = minus(other.toLong())
    external operator fun minus(other: Long): Long
    operator fun minus(other: Half): Half = toHalf() - other
    operator fun minus(other: Float): Float = toFloat() - other
    operator fun minus(other: Double): Double = toDouble() - other

    operator fun times(other: Int): Long = times(other.toLong())
    external operator fun times(other: Long): Long
    operator fun times(other: Half): Half = toHalf() * other
    operator fun times(other: Float): Float = toFloat() * other
    operator fun times(other: Double): Double = toDouble() * other

    operator fun div(other: Int): Long = div(other.toLong())
    external operator fun div(other: Long): Long
    operator fun div(other: Half): Half = toHalf() / other
    operator fun div(other: Float): Float = toFloat() / other
    operator fun div(other: Double): Double = toDouble() / other

    operator fun rem(other: Int): Long = rem(other.toLong())
    external operator fun rem(other: Long): Long
    operator fun rem(other: Half): Half = toHalf() % other
    operator fun rem(other: Float): Float = toFloat() % other
    operator fun rem(other: Double): Double = toDouble() % other

    external infix fun or(other: Long): Long
    external infix fun and(other: Long): Long
    external infix fun xor(other: Long): Long
    external fun inv(): Long

    infix fun or(other: Int): Long = or(other.toLong())
    infix fun and(other: Int): Long = and(other.toLong())
    infix fun xor(other: Int): Long = xor(other.toLong())

    external fun toInt(): Int
    fun toLong(): Long = this
    external fun toHalf(): Half
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Long = this + 1L
    override operator fun dec(): Long = this - 1L

    external override fun unaryMinus(): Self

    infix fun until(other: Int): LongRange = LongRange(this, other)
    infix fun rangeTo(other: Int): LongRange = LongRange(this, other + 1)

    override fun compareTo(other: Int): Int = compareTo(other.toLong())
    external override fun compareTo(other: Long): Int
    override fun compareTo(other: Half): Int = compareTo(other.toDouble())
    override fun compareTo(other: Float): Int = compareTo(other.toDouble())
    external override fun compareTo(other: Double): Int
}

value class Half(val value: NativeF16) : Number,
    Comparable<Int>, Comparable<Long>, Comparable<Float>, Comparable<Double> {

    operator fun plus(other: Int): Half = plus(other.toLong())
    external operator fun plus(other: Long): Half
    external operator fun plus(other: Half): Half
    operator fun plus(other: Float): Float = toFloat().plus(other)
    operator fun plus(other: Double): Double = toDouble().plus(other)

    operator fun minus(other: Int): Half = minus(other.toLong())
    external operator fun minus(other: Long): Half
    external operator fun minus(other: Half): Half
    operator fun minus(other: Float): Float = toFloat().minus(other)
    operator fun minus(other: Double): Double = toDouble().minus(other)

    operator fun times(other: Int): Half = times(other.toLong())
    external operator fun times(other: Long): Half
    external operator fun times(other: Half): Half
    operator fun times(other: Float): Float = toFloat().times(other)
    operator fun times(other: Double): Double = toDouble().times(other)

    operator fun div(other: Int): Half = div(other.toLong())
    external operator fun div(other: Long): Half
    external operator fun div(other: Half): Half
    operator fun div(other: Float): Float = toFloat().div(other)
    operator fun div(other: Double): Double = toDouble().div(other)

    operator fun rem(other: Int): Half = rem(other.toLong())
    external operator fun rem(other: Long): Half
    external operator fun rem(other: Half): Half
    operator fun rem(other: Float): Float = toFloat().rem(other)
    operator fun rem(other: Double): Double = toDouble().rem(other)

    external fun toInt(): Int
    external fun toLong(): Long
    fun toHalf(): Half = this
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Half = this + 1
    override operator fun dec(): Half = this - 1

    external override fun unaryMinus(): Self

    override fun compareTo(other: Int): Int = compareTo(other.toLong())
    external override fun compareTo(other: Long): Int
    external override fun compareTo(other: Half): Int
    override fun compareTo(other: Float): Int = toFloat().compareTo(other)
    override fun compareTo(other: Double): Int = toDouble().compareTo(other)
}

value class Float(val value: NativeF32) : Number,
    Comparable<Int>, Comparable<Long>, Comparable<Half>, Comparable<Double> {

    operator fun plus(other: Int): Float = plus(other.toLong())
    external operator fun plus(other: Long): Float
    operator fun plus(other: Half): Float = plus(other.toFloat())
    external operator fun plus(other: Float): Float
    operator fun plus(other: Double): Double = toDouble().plus(other)

    operator fun minus(other: Int): Float = minus(other.toLong())
    external operator fun minus(other: Long): Float
    operator fun minus(other: Half): Float = minus(other.toFloat())
    external operator fun minus(other: Float): Float
    operator fun minus(other: Double): Double = toDouble().minus(other)

    operator fun times(other: Int): Float = times(other.toLong())
    external operator fun times(other: Long): Float
    operator fun times(other: Half): Float = times(other.toFloat())
    external operator fun times(other: Float): Float
    operator fun times(other: Double): Double = toDouble().times(other)

    operator fun div(other: Int): Float = div(other.toLong())
    external operator fun div(other: Long): Float
    operator fun div(other: Half): Float = div(other.toFloat())
    external operator fun div(other: Float): Float
    operator fun div(other: Double): Double = toDouble().div(other)

    operator fun rem(other: Int): Float = rem(other.toLong())
    external operator fun rem(other: Long): Float
    operator fun rem(other: Half): Float = rem(other.toFloat())
    external operator fun rem(other: Float): Float
    operator fun rem(other: Double): Double = toDouble().rem(other)

    external fun toInt(): Int
    external fun toLong(): Long
    external fun toHalf(): Half
    fun toFloat(): Float = this
    external fun toDouble(): Double

    override operator fun inc(): Float = this + 1f
    override operator fun dec(): Float = this - 1f

    external override fun unaryMinus(): Self

    override fun compareTo(other: Int): Int = compareTo(other.toLong())
    external override fun compareTo(other: Long): Int
    override fun compareTo(other: Half): Int = compareTo(other.toFloat())
    external override fun compareTo(other: Float): Int
    override fun compareTo(other: Double): Int = toDouble().compareTo(other)
}

value class Double(val value: NativeF64) : Number,
    Comparable<Int>, Comparable<Long>, Comparable<Half>, Comparable<Float> {

    operator fun plus(other: Int): Double = plus(other.toLong())
    external operator fun plus(other: Long): Double
    operator fun plus(other: Half): Double = plus(other.toDouble())
    operator fun plus(other: Float): Double = plus(other.toDouble())
    external operator fun plus(other: Double): Double

    operator fun minus(other: Int): Double = minus(other.toLong())
    external operator fun minus(other: Long): Double
    operator fun minus(other: Half): Double = minus(other.toDouble())
    operator fun minus(other: Float): Double = minus(other.toDouble())
    external operator fun minus(other: Double): Double

    operator fun times(other: Int): Double = times(other.toLong())
    external operator fun times(other: Long): Double
    operator fun times(other: Half): Double = times(other.toDouble())
    operator fun times(other: Float): Double = times(other.toDouble())
    external operator fun times(other: Double): Double

    operator fun div(other: Int): Double = div(other.toLong())
    external operator fun div(other: Long): Double
    operator fun div(other: Half): Double = div(other.toDouble())
    operator fun div(other: Float): Double = div(other.toDouble())
    external operator fun div(other: Double): Double

    operator fun rem(other: Int): Double = rem(other.toLong())
    external operator fun rem(other: Long): Double
    operator fun rem(other: Half): Double = rem(other.toDouble())
    operator fun rem(other: Float): Double = rem(other.toDouble())
    external operator fun rem(other: Double): Double

    external fun toInt(): Int
    external fun toLong(): Long
    external fun toHalf(): Half
    external fun toFloat(): Float
    fun toDouble(): Double = this

    override operator fun inc(): Double = this + 1.0
    override operator fun dec(): Double = this - 1.0

    external override fun unaryMinus(): Self

    override fun compareTo(other: Int): Int = compareTo(other.toLong())
    external override fun compareTo(other: Long): Int
    override fun compareTo(other: Half): Int = compareTo(other.toDouble())
    override fun compareTo(other: Float): Int = compareTo(other.toDouble())
    external override fun compareTo(other: Double): Int
}