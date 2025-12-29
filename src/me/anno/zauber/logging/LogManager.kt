package me.anno.zauber.logging

import kotlin.reflect.KClass

object LogManager {
    fun getLogger(name: String, debug: Boolean = false): Logger = Logger(name,debug)
    fun getLogger(clazz: KClass<*>, debug: Boolean = false): Logger = getLogger(clazz.java.simpleName,debug)
}