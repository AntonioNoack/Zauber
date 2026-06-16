package me.anno.support.jvm

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import me.anno.utils.assertEquals
import me.anno.zauber.expansion.MethodOverrides.debuggedMethodName
import me.anno.zauber.interpreting.ExternalMethod
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.types.getScope
import me.anno.zauber.types.getScope0


// todo next step:
//   getClass() -> ClassType -> Java calls getClassLoader() -> we need to define our own classLoader...


// todo read a complex class like HashMap,
//  and decode it fully into simple instructions...

// todo ideally, we have some jars and can just lazy-load all contents

// then try to instantiate and use an instance...
// todo we need to fix generics... ArrayList.add() must return E, not Object

// todo is there an interesting, non-generic class we can test?
//  -> we could use any class we create, and Kotlin/Java compiles for us...

// todo why TF is this exploring Regex???

fun main() {

    LogManager.disableLoggersCompletely("OverriddenMethods")

    // define some standard functions...
    testExecute(
        """
val tested = 0 // unused

package zauber
class Int {
    external operator fun plus(other: Int): Int
    external operator fun minus(other: Int): Int
    external operator fun times(other: Int): Int
    external operator fun div(other: Int): Int
    external operator fun rem(other: Int): Int
    external operator fun compareTo(other: Int): Int
    operator fun unaryMinus(): Int = 0 - this
    external fun equals(other: Int): Boolean
    external operator fun and(other: Int): Int
    external operator fun or(other: Int): Int
    external operator fun xor(other: Int): Int
    external operator fun shl(other: Int): Int
    external operator fun shr(other: Int): Int
    external operator fun ushr(other: Int): Int
    external operator fun toChar(): Char
    external operator fun toByte(): Byte
    external operator fun toShort(): Short
    external operator fun toLong(): Long
    external operator fun toFloat(): Float
    external operator fun toDouble(): Double
    fun toBoolean() = this != 0
}
class Byte {
    external fun toInt(): Int
}
class Short {
    external fun toInt(): Int
}
class Char {
    external fun toInt(): Int
}
class Long {
    external operator fun plus(other: Long): Long
    external operator fun minus(other: Long): Long
    external operator fun times(other: Long): Long
    external operator fun div(other: Long): Long
    external operator fun rem(other: Long): Long
    external operator fun compareTo(other: Long): Int
    operator fun unaryMinus(): Long = 0L - this
    external fun equals(other: Long): Boolean
    external operator fun and(other: Long): Long
    external operator fun or(other: Long): Long
    external operator fun xor(other: Long): Long
    external operator fun shl(other: Int): Long
    external operator fun shr(other: Int): Long
    external operator fun ushr(other: Int): Long
    external operator fun toInt(): Int
}
class Float {
    external operator fun plus(other: Float): Float
    external operator fun minus(other: Float): Float
    external operator fun times(other: Float): Float
    external operator fun div(other: Float): Float
    external operator fun rem(other: Float): Float
    external operator fun compareTo(other: Float): Int
    external fun toInt(): Int
}
class Double {
    external operator fun plus(other: Double): Double
    external operator fun minus(other: Double): Double
    external operator fun times(other: Double): Double
    external operator fun div(other: Double): Double
    external operator fun rem(other: Double): Double
    external operator fun compareTo(other: Double): Int
    external fun toInt(): Int
}
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    external operator fun set(index: Int, value: Any)
    external operator fun get(index: Int): V
}
enum class Boolean {
    TRUE, FALSE;
    
    fun toInt(): Int = if (this) 1 else 0
}

import java.lang.ClassLoader
object ZauberClassLoader: ClassLoader() {
    
}
class ClassType<V> {
    var classLoader: ClassLoader = ZauberClassLoader
}
open class Throwable(val message: String)
open class Exception(msg: String): Throwable(msg)
class ClassCastException(): Exception("Cast failed")
    """.trimIndent()
    )

    debuggedMethodName = "getAnnotation"

    LogManager.enableDebug(
        "Runtime," +
                "SimpleGetClassField,SimpleSetClassField," +
                "SimpleGetLocalField,SimpleSetLocalField"
    )

    registerJavaClass("java.util.ArrayList")
    runtime.register(getScope0("java.lang.ClassLoader.Companion"), "registerNatives", emptyList()) { _, _ ->
        runtime.getUnit()
    }
    runtime.register(getScope0("java.lang.System.Companion"), "registerNatives", emptyList()) { _, _ ->
        runtime.getUnit()
    }

    val value = testExecute(
        """
        import java.util.ArrayList
        fun test(): Int {
            val x = ArrayList<Int>()
            x.add(1)
            return x[0]
        }
        
        val tested = test()
    """.trimIndent(), reset = false
    )
    assertEquals(1, value.castToInt())

}

fun registerJavaClass(path: String) {
    FirstJVMClassReader.getScope(path, null)
}