package me.anno.generation.jvm


sealed interface CpEntry {
    fun writeTo(w: JVMBytecodeWriter)
}

data class CpClass(val nameIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(7) // CONSTANT_Class
        w.u2(nameIndex)
    }
}

data class CpString(val stringIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(8) // CONSTANT_String
        w.u2(stringIndex)
    }
}

data class NameAndType(val nameIndex: Int, val descriptorIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(12) // CONSTANT_NameAndType
        w.u2(nameIndex)
        w.u2(descriptorIndex)
    }
}

data class FieldRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(9) // CONSTANT_Fieldref
        w.u2(classIndex)
        w.u2(nameAndTypeIndex)
    }
}

data class MethodRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(10) // CONSTANT_Methodref
        w.u2(classIndex)
        w.u2(nameAndTypeIndex)
    }
}

data class InterfaceMethodRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry {
    override fun writeTo(w: JVMBytecodeWriter) {
        w.u1(11) // CONSTANT_InterfaceMethodref
        w.u2(classIndex)
        w.u2(nameAndTypeIndex)
    }
}
