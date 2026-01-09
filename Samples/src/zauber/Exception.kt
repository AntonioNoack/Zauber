package zauber

open class Throwable(val message: String? = null) {}
open class Exception(message: String? = null) : Throwable(message)
open class Error(message: String? = null) : Throwable(message)
open class RuntimeException(message: String? = null) : Exception(message)
class NullPointerException(message: String? = null) : RuntimeException(message)
class IllegalArgumentException(message: String? = null) : RuntimeException(message)
class IllegalStateException(message: String? = null) : RuntimeException(message)

fun throwNPE(message: String? = null): Nothing {
    throw NullPointerException(message)
}