package zauber

import zauber.types.Self
import kotlin.reflect.KClass

class Any {

    open fun toString(): String = "${clazz.simpleName}@${hashCode()}"
    open fun hashCode(): Int = System.identityHashCode(this)

    private external val clazz: KClass<Self>
}