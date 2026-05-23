package me.anno.generation.jvm

import me.anno.utils.ByteArrayOutputStream2
import java.io.DataOutputStream

/**
 * Low-level writer for JVM classfile / bytecode structures.
 *
 * The JVM classfile format is big-endian.
 * This is the rough JVM-equivalent of [me.anno.generation.wasm.WASMBinaryWriter].
 */
class JVMBytecodeWriter(val out: ByteArrayOutputStream2 = ByteArrayOutputStream2()) {

    val dos = DataOutputStream(out)

    fun u1(v: Int) {
        out.write(v)
    }

    fun u2(v: Int) {
        check(v.and(0xffff) == v)
        dos.writeShort(v)
    }

    fun u4(v: Int) {
        dos.writeInt(v)
    }

    fun bytes(v: ByteArray) {
        out.write(v)
    }

    /**
     * JVM classfile CONSTANT_Utf8 uses "modified UTF-8" (same as DataOutputStream.writeUTF()).
     * We encode that payload and write its length prefix (u2) + bytes.
     */
    fun modifiedUtf8(v: String) {
        dos.writeUTF(v)
    }

    /**
     * Writes the classfile header (magic + version).
     *
     * Major versions: 52=Java8, 55=Java11, 61=Java17, 65=Java21, ...
     */
    fun writeClassfileHeader(minor: Int = 0, major: Int = 52) {
        // magic
        u4(0xCAFEBABE.toInt())
        u2(minor)
        u2(major)
    }

    /**
     * Writes a full ClassFile structure in spec order.
     *
     * Indices such as [thisClass], [superClass], [interfaces], and attribute name indices
     * are constant-pool indices (1-based).
     */
    fun writeClassFile(
        minor: Int,
        major: Int,
        constantPool: ConstantPool,
        accessFlags: Int,
        thisClass: Int,
        superClass: Int,
        interfaces: IntArray = IntArray(0),
        fields: List<FieldInfo> = emptyList(),
        methods: List<MethodInfo> = emptyList(),
        attributes: List<AttributeInfo> = emptyList()
    ) {
        writeClassfileHeader(minor, major)
        writeConstantPool(constantPool)
        u2(accessFlags)
        u2(thisClass)
        u2(superClass)
        writeInterfaces(interfaces)
        writeFields(fields)
        writeMethods(methods)
        writeAttributes(attributes)
    }

    fun writeInterfaces(interfaces: IntArray) {
        u2(interfaces.size)
        for (i in interfaces) u2(i)
    }

    fun writeFields(fields: List<FieldInfo>) {
        u2(fields.size)
        for (f in fields) {
            u2(f.accessFlags)
            u2(f.nameIndex)
            u2(f.descriptorIndex)
            writeAttributes(f.attributes)
        }
    }

    fun writeMethods(methods: List<MethodInfo>) {
        u2(methods.size)
        for (m in methods) {
            u2(m.accessFlags)
            u2(m.nameIndex)
            u2(m.descriptorIndex)
            writeAttributes(m.attributes)
        }
    }

    fun writeAttributes(attributes: List<AttributeInfo>) {
        u2(attributes.size)
        for (a in attributes) {
            u2(a.nameIndex)
            u4(a.info.size)
            bytes(a.info)
        }
    }

    fun writeConstantPool(constantPool: ConstantPool) {
        // constant_pool_count is 1 + actual entries (index 0 is invalid)
        u2(constantPool.poolSize)
        constantPool.writeTo(this)
    }

    /**
     * Convenience for creating a JVM "Code" attribute info blob.
     *
     * Caller is expected to wrap the returned bytes into an [AttributeInfo] where nameIndex points
     * to a CONSTANT_Utf8 "Code" entry.
     */
    fun buildCodeAttributeInfo(
        maxStack: Int,
        maxLocals: Int,
        code: ByteArray,
        exceptionTable: List<ExceptionTableEntry> = emptyList(),
        attributes: List<AttributeInfo> = emptyList()
    ): ByteArray {
        val w = JVMBytecodeWriter()
        w.u2(maxStack)
        w.u2(maxLocals)
        w.u4(code.size)
        w.bytes(code)
        w.u2(exceptionTable.size)
        for (e in exceptionTable) {
            w.u2(e.startPc)
            w.u2(e.endPc)
            w.u2(e.handlerPc)
            w.u2(e.catchType)
        }
        w.writeAttributes(attributes)
        return w.out.toByteArray()
    }
}
