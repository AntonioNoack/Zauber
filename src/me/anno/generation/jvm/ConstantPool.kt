package me.anno.generation.jvm


/**
 * Constant pool builder/writer.
 *
 * This keeps indices stable and handles "double-slot" entries (Long/Double) by inserting a hole.
 * Indices returned are 1-based as per the JVM specification.
 */
class ConstantPool {

    private val entries = ArrayList<Any>(64)
    private var nextIndex = 1 // index 0 is invalid

    private val utf8 = HashMap<String, Int>()
    private val classes = HashMap<CpClass, Int>()
    private val strings = HashMap<String, Int>()
    private val nameAndTypes = HashMap<NameAndType, Int>()
    private val fieldRefs = HashMap<FieldRef, Int>()
    private val methodRefs = HashMap<MethodRef, Int>()
    private val interfaceMethodRefs = HashMap<InterfaceMethodRef, Int>()

    val poolSize: Int get() = nextIndex

    private fun addEntry(entry: Any, takesTwoSlots: Boolean = false): Int {
        entries.add(entry)
        val index = nextIndex
        nextIndex = index + if (takesTwoSlots) 2 else 1
        return index
    }

    fun utf8(v: String): Int {
        return utf8.getOrPut(v) { addEntry(v) }
    }

    fun clazz(internalName: String): Int {
        val nameIndex = utf8(internalName)
        val key = CpClass(nameIndex)
        return classes.getOrPut(key) { addEntry(key) }
    }

    fun string(value: String): Int {
        val utf8Index = utf8(value)
        return strings.getOrPut(value) {
            addEntry(CpString(utf8Index))
        }
    }

    fun nameAndType(name: String, descriptor: String): Int {
        val nameIndex = utf8(name)
        val descIndex = utf8(descriptor)
        val key = NameAndType(nameIndex, descIndex)
        return nameAndTypes.getOrPut(key) {
            addEntry(key)
        }
    }

    fun fieldRef(classInternalName: String, name: String, descriptor: String): Int {
        val classIndex = clazz(classInternalName)
        val natIndex = nameAndType(name, descriptor)
        val key = FieldRef(classIndex, natIndex)
        return fieldRefs.getOrPut(key) { addEntry(key) }
    }

    fun methodRef(classInternalName: String, name: String, descriptor: String): Int {
        val classIndex = clazz(classInternalName)
        val natIndex = nameAndType(name, descriptor)
        val key = MethodRef(classIndex, natIndex)
        return methodRefs.getOrPut(key) { addEntry(key) }
    }

    fun interfaceMethodRef(classInternalName: String, name: String, descriptor: String): Int {
        val classIndex = clazz(classInternalName)
        val natIndex = nameAndType(name, descriptor)
        val key = InterfaceMethodRef(classIndex, natIndex)
        return interfaceMethodRefs.getOrPut(key) { addEntry(key) }
    }

    fun int(v: Int): Int = addEntry(v)
    fun float(v: Float): Int = addEntry(v)
    fun long(v: Long): Int = addEntry(v, takesTwoSlots = true)
    fun double(v: Double): Int = addEntry(v, takesTwoSlots = true)

    fun writeTo(w: JVMBytecodeWriter) {
        for (i in entries.indices) {
            when (val entry = entries[i]) {
                is String -> {
                    w.u1(1) // CONSTANT_Utf8
                    w.dos.writeUTF(entry)
                }
                is Int -> {
                    w.u1(3) // CONSTANT_Integer
                    w.dos.writeInt(entry)
                }
                is Float -> {
                    w.u1(4) // CONSTANT_Float
                    w.dos.writeFloat(entry)
                }
                is Long -> {
                    w.u1(5) // CONSTANT_Long
                    w.dos.writeLong(entry)
                }
                is Double -> {
                    w.u1(6) // CONSTANT_Double
                    w.dos.writeDouble(entry)
                }
                is CpEntry -> entry.writeTo(w)
                else -> throw NotImplementedError("Write pool entry ${entry.javaClass.simpleName}")
            }
        }
    }
}
