package zauber

import zauber.types.Self
import kotlin.reflect.KClass

class Any {

    /**
     * Converts the instance into a readable representation
     * */
    open fun toString(): String = "${clazz.simpleName}@${hashCode()}"

    /**
     * Gets the code for HashMaps,
     * by default something related to the pointer,
     * or for value- and data objects from their bytes.
     * */
    open fun hashCode(): Int = System.identityHashCode(this)

    /**
     * This is called when a value object goes out of scope, or a reference object is garbage collected.
     * */
    open fun finalize() {}

    private external val clazz: KClass<Self>

    @Deprecated("Only exists for JVM compatibility")
    external val javaClass: Class<Any>

}