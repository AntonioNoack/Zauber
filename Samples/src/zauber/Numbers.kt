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
}

value class Int(val value: NativeI32) : Number {

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

    fun toInt(): Int = this
    external fun toLong(): Long
    external fun toHalf(): Half
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Int = this + 1
    override operator fun dec(): Int = this - 1

    external override fun compareTo(other: Self): Int
}

value class Long(val value: NativeI64) : Number {

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

    external fun toInt(): Int
    fun toLong(): Long = this
    external fun toHalf(): Half
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Long = this + 1L
    override operator fun dec(): Long = this - 1L

    external override fun compareTo(other: Self): Int
}

value class Half(val value: NativeF16) : Number {

    external operator fun plus(other: Half): Half
    external operator fun minus(other: Half): Half
    external operator fun times(other: Half): Half
    external operator fun div(other: Half): Half
    external operator fun rem(other: Half): Half

    external fun toInt(): Int
    external fun toLong(): Long
    fun toHalf(): Half = this
    external fun toFloat(): Float
    external fun toDouble(): Double

    override operator fun inc(): Half = this + 1h
    override operator fun dec(): Half = this - 1h

    external override fun compareTo(other: Self): Int
}

value class Float(val value: NativeF32) : Number {
    operator fun plus(other: Float): Float = native("this + other")
    operator fun minus(other: Float): Float = native("this - other")
    operator fun times(other: Float): Float = native("this * other")
    operator fun div(other: Float): Float = native("this / other")
    operator fun rem(other: Float): Float = native("this % other")

    fun toInt(): Int = native("s32(this)")
    fun toLong(): Long = native("s64(this)")
    fun toHalf(): Half = native("f16(this)")
    fun toFloat(): Float = this
    fun toDouble(): Double = native("f64(this)")

    override operator fun inc(): Float = this + 1f
    override operator fun dec(): Float = this - 1f

    external override fun compareTo(other: Self): Int
}

value class Double(val value: NativeF64) : Number {
    external override operator fun plus(other: Self): Self
    external override operator fun minus(other: Self): Self
    external override operator fun times(other: Self): Self
    external override operator fun div(other: Self): Self
    external override operator fun rem(other: Self): Self

    external fun toInt(): Int
    external fun toLong(): Long
    external fun toHalf(): Half
    external fun toFloat(): Float
    fun toDouble(): Double = this

    override operator fun inc(): Double = this + 1.0
    override operator fun dec(): Double = this - 1.0

    external override fun compareTo(other: Self): Int
}