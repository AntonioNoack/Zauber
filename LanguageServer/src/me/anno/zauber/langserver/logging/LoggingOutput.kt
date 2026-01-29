package me.anno.zauber.langserver.logging

import java.io.OutputStream

object LoggingOutput : OutputStream() {
    private val buffer = ByteArray(4096)
    private var count = 0

    override fun write(b: ByteArray) {
        write(b, 0, count)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            write(b[i].toInt().and(255))
        }
    }

    override fun write(b: Int) {
        if (count == buffer.size) flush()
        buffer[count++] = b.toByte()
        if (b == '\n'.code) flush()
    }

    override fun flush() {
        if (count > 0) {
            AppendLogging.append(buffer.decodeToString(0, count))
            count = 0
        }
    }
}
