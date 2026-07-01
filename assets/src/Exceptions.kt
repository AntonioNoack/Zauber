package zauber

class Throwable(val message: String)
class Exception(message: String): Throwable(message)
class RuntimeException(message: String) : Exception(message)
class NullPointerException(message: String) : RuntimeException(message)
class IllegalArgumentException(message: String) : RuntimeException(message)

/**
 * not yet initialized
 * */
fun throwNJI(name: String): Nothing {
    throw NullPointerException(name)
}

/**
 * null-pointer exception
 * */
fun throwNPE(message: String): Nothing {
    throw NullPointerException(message)
}
