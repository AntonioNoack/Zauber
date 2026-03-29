package me.anno.zauber.logging

import kotlin.reflect.KClass

object LogManager {

    private val loggers = HashMap<String, Logger>()

    fun getLogger(name: String, debug: Boolean = false): Logger {
        return loggers.getOrPut(name) { Logger(name, debug) }
    }

    fun getLogger(clazz: KClass<*>, debug: Boolean = false): Logger {
        return getLogger(clazz.java.simpleName, debug)
    }

    fun enableDebug(loggers: String) {
        for (name in loggers.split(',')) {
            val logger = getLogger(name, true)
            logger.isInfoEnabled = true
            logger.isDebugEnabled = true
        }
    }

    fun disableLoggers(loggers: String) {
        for (name in loggers.split(',')) {
            val logger = getLogger(name, false)
            logger.isInfoEnabled = false
            logger.isDebugEnabled = false
        }
    }
}