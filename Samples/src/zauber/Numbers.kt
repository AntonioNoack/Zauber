package zauber

import zauber.types.Self

interface Number : Comparable<Self> {
    operator fun plus(other: Self): Self
    operator fun minus(other: Self): Self
    operator fun times(other: Self): Self
    operator fun div(other: Self): Self
    operator fun rem(other: Self): Self
}

value class Int(val value: NativeI32) : Number {

    operator fun plus(other: Int): Int = native("this + other")
    operator fun plus(other: Long): Long = toLong() + other
    operator fun plus(other: Half): Half = toHalf() + other
    operator fun plus(other: Float): Float = toFloat() + other
    operator fun plus(other: Double): Double = toDouble() + other

    operator fun minus(other: Int): Int = native("this - other")
    operator fun minus(other: Long): Long = toLong() - other
    operator fun minus(other: Half): Half = toHalf() - other
    operator fun minus(other: Float): Float = toFloat() - other
    operator fun minus(other: Double): Double = toDouble() - other

    operator fun times(other: Int): Int = native("this * other")
    operator fun times(other: Long): Long = toLong() * other
    operator fun times(other: Half): Half = toHalf() * other
    operator fun times(other: Float): Float = toFloat() * other
    operator fun times(other: Double): Double = toDouble() * other

    operator fun div(other: Int): Int = native("this / other")
    operator fun div(other: Long): Long = toLong() / other
    operator fun div(other: Half): Half = toHalf() / other
    operator fun div(other: Float): Float = toFloat() / other
    operator fun div(other: Double): Double = toDouble() / other

    operator fun rem(other: Int): Int = native("this % other")
    operator fun rem(other: Long): Long = toLong() % other
    operator fun rem(other: Half): Half = toHalf() % other
    operator fun rem(other: Float): Float = toFloat() % other
    operator fun rem(other: Double): Double = toDouble() % other

    fun toInt(): Int = this
    fun toLong(): Long = native("s64(this)")
    fun toHalf(): Half = native("f16(this)")
    fun toFloat(): Float = native("f32(this)")
    fun toDouble(): Double = native("f64(this)")

    fun inc(): Int = this + 1
    fun dec(): Int = this - 1

    override fun compareTo(other: Self): Int {
        return if (this > other) +1 else if (this >= other) 0 else -1
    }
}

value class Long(val value: NativeI64) : Number {

    operator fun plus(other: Int): Long = plus(other.toLong())
    operator fun plus(other: Long): Long = native("this + other")
    operator fun plus(other: Half): Half = toHalf() + other
    operator fun plus(other: Float): Float = toFloat() + other
    operator fun plus(other: Double): Double = toDouble() + other

    operator fun minus(other: Int): Long = minus(other.toLong())
    operator fun minus(other: Long): Long = native("this - other")
    operator fun minus(other: Half): Half = toHalf() - other
    operator fun minus(other: Float): Float = toFloat() - other
    operator fun minus(other: Double): Double = toDouble() - other

    operator fun times(other: Int): Long = times(other.toLong())
    operator fun times(other: Long): Long = native("this * other")
    operator fun times(other: Half): Half = toHalf() * other
    operator fun times(other: Float): Float = toFloat() * other
    operator fun times(other: Double): Double = toDouble() * other

    operator fun div(other: Int): Long = div(other.toLong())
    operator fun div(other: Long): Long = native("this / other")
    operator fun div(other: Half): Half = toHalf() / other
    operator fun div(other: Float): Float = toFloat() / other
    operator fun div(other: Double): Double = toDouble() / other

    operator fun rem(other: Int): Long = rem(other.toLong())
    operator fun rem(other: Long): Long = native("this % other")
    operator fun rem(other: Half): Half = toHalf() % other
    operator fun rem(other: Float): Float = toFloat() % other
    operator fun rem(other: Double): Double = toDouble() % other

    fun toInt(): Int = native("s32(this)")
    fun toLong(): Long = this
    fun toHalf(): Half = native("f16(this)") // this is stupid, half has a smaller range...
    fun toFloat(): Float = native("f32(this)")
    fun toDouble(): Double = native("f64(this)")

    fun inc(): Long = this + 1L
    fun dec(): Long = this - 1L

    override fun compareTo(other: Self): kotlin.Int {
        return if (this > other) +1 else if (this >= other) 0 else -1
    }
}

value class Half(val value: NativeF16) : Number {

    operator fun plus(other: Half): Half = native("this + other")
    operator fun minus(other: Half): Half = native("this - other")
    operator fun times(other: Half): Half = native("this * other")
    operator fun div(other: Half): Half = native("this / other")
    operator fun rem(other: Half): Half = native("this % other")

    fun toInt(): Int = native("s32(this)")
    fun toLong(): Long = native("s64(this)")
    fun toHalf(): Half = this
    fun toFloat(): Float = native("s32(this)")
    fun toDouble(): Double = native("f64(this)")

    override fun compareTo(other: Half): Int {
        return if (this > other) +1 else if (this >= other) 0 else -1
    }
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

    fun inc(): Float = this + 1f
    fun dec(): Float = this - 1f

    override fun compareTo(other: Float): Int {
        return if (this > other) +1 else if (this >= other) 0 else -1
    }
}

value class Double(val value: NativeF64) : Number {
    operator fun plus(other: Double): Double = native("this + other")
    operator fun minus(other: Double): Double = native("this - other")
    operator fun times(other: Double): Double = native("this * other")
    operator fun div(other: Double): Double = native("this / other")
    operator fun rem(other: Double): Double = native("this % other")

    fun toInt(): Int = native("s32(this)")
    fun toLong(): Long = native("s64(this)")
    fun toHalf(): Half = native("f16(this)")
    fun toFloat(): Float = native("f32(this)")
    fun toDouble(): Double = this

    fun inc(): Double = this + 1.0
    fun dec(): Double = this - 1.0

    override fun compareTo(other: Double): Int {
        return if (this > other) +1 else if (this >= other) 0 else -1
    }
}