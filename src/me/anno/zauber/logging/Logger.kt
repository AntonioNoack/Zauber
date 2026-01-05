package me.anno.zauber.logging

import java.io.PrintStream
import java.util.*

class Logger(val name: String, var enableDebug: Boolean) {

    var enableInfo: Boolean = true
    var enableWarn: Boolean = true

    private val knownWarnings = HashSet<String>()

    private fun infoImpl(prefix: String, message: String, stream: PrintStream) {
        if ('\n' !in message) {
            stream.println("[${getTime()},$name:$prefix] $message")
        } else {
            val lines = message.split('\n')
            infoImpl(prefix, lines.first(), stream)
            for (i in 1 until lines.size) {
                stream.println("    " + lines[i])
            }
        }
    }

    fun info(message: String) {
        if (enableInfo) infoImpl("INFO", message, System.out)
    }

    fun warn(message: String) {
        if (enableWarn && knownWarnings.add(message)) {
            infoImpl("ERR", message, System.err)
        }
    }

    fun debug(message: String) {
        if (enableDebug) infoImpl("DEBUG", message, System.out)
    }

    companion object {
        private fun getTime(): String {
            val calendar = Calendar.getInstance()
            val millis = calendar.get(Calendar.MILLISECOND)
            val seconds = calendar.get(Calendar.SECOND)
            val minutes = calendar.get(Calendar.MINUTE)
            val hours = calendar.get(Calendar.HOUR_OF_DAY)
            return formatTime(hours, minutes, seconds, millis).toString()
        }

        private val lastTimeStr = StringBuilder(16).append("hh:mm:ss.sss")
        private fun formatTime(hours: Int, minutes: Int, seconds: Int, millis: Int): StringBuilder {
            format10s(hours, 0)
            format10s(minutes, 3)
            format10s(seconds, 6)
            format100s(millis, 9)
            return lastTimeStr
        }

        private fun format10s(h: Int, i: Int) {
            lastTimeStr[i] = '0' + (h / 10)
            lastTimeStr[i + 1] = '0' + (h % 10)
        }

        @Suppress("SameParameterValue")
        private fun format100s(h: Int, i: Int) {
            format10s(h / 10, i)
            lastTimeStr[i + 2] = '0' + (h % 10)
        }
    }

}