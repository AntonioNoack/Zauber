class File

interface Closeable {
    fun close()
}

abstract class InputStream: Closeable {
    abstract fun read(): Int
    open fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)
    open fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        val value = read()
        if (value < 0) return value
        bytes[offset] = value
        return 1
    }
    override fun close() {}
}

abstract class OutputStream: Closeable {
    abstract fun write(byte: Int)
    open fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    open fun write(bytes: ByteArray, offset: Int, length: Int) {
        var i = offset
        var end = offset + length
        while (i < end) {
            write(bytes[i++])
        }
    }
    override fun close() {}
}