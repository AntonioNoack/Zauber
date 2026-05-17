package me.anno.zauber.typeresolution

import me.anno.zauber.types.Type

/**
 * abstractly defines a value parameter:
 *   given a certain target type, what would we produce?
 * */
abstract class ValueParameter(val name: String?, val hasVarargStar: Boolean) {
    abstract fun getType(targetType: Type): Type
}