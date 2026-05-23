package me.anno.generation.jvm

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JVMBytecodeWriterTest {

    @Test
    fun testBigEndianPrimitives() {
        val w = JVMBytecodeWriter()
        w.u1(0x12)
        w.u2(0x3456)
        w.u4(0x789ABCDE)
        val out = w.out.toByteArray()
        assertArrayEquals(
            byteArrayOf(
                0x12,
                0x34, 0x56,
                0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte()
            ),
            out
        )
    }

    @Test
    fun testModifiedUtf8IncludesLengthPrefix() {
        val w = JVMBytecodeWriter()
        w.modifiedUtf8("A\u0000B") // NUL must become 0xC0 0x80
        val out = w.out.bytes.copyOf(w.out.size)
        // payload is: 'A' (41), NUL (C0 80), 'B' (42) => 4 bytes
        assertEquals(6, out.size)
        assertArrayEquals(
            byteArrayOf(
                0x00, 0x04,
                0x41, 0xC0.toByte(), 0x80.toByte(), 0x42
            ),
            out
        )
    }
}

