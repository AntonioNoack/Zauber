package me.anno.zauber.logging

import me.anno.utils.StringStyles
import me.anno.utils.StringStyles.style
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class Logger(val name: String, var isDebugEnabled: Boolean) {

    var isInfoEnabled: Boolean = true
    var isWarnEnabled: Boolean = true

    private val knownWarnings = HashSet<String>()

    private fun infoImpl(prefix: String, message: String, stream: PrintStream) {
        if ('\n' !in message) {
            val time = style(getTime(), timeStyle)
            stream.println("[$time,$name:$prefix] $message")
        } else {
            val lines = message.split('\n')
            infoImpl(prefix, lines.first(), stream)
            for (i in 1 until lines.size) {
                stream.println("    " + lines[i])
            }
        }
    }

    fun info(message: String) {
        if (isInfoEnabled) infoImpl("INFO", message, System.out)
    }

    fun warn(message: String) {
        if (isWarnEnabled && knownWarnings.add(message)) {
            infoImpl("ERR", message, System.err)
        }
    }

    fun warn(message: String, e: Throwable) {
        val sb = StringWriter()
        sb.append(message).append(": ")
        val pr = PrintWriter(sb)
        e.printStackTrace(pr)
        warn(sb.toString())
    }

    fun debug(message: String) {
        if (isDebugEnabled) infoImpl("DEBUG", message, System.out)
    }

    companion object {

        val timeStyle = StringStyles.ITALIC + StringStyles.color(0x777777)

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