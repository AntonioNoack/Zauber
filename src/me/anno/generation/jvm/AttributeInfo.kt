package me.anno.generation.jvm

data class AttributeInfo(val nameIndex: Int, val info: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttributeInfo

        if (nameIndex != other.nameIndex) return false
        if (!info.contentEquals(other.info)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nameIndex
        result = 31 * result + info.contentHashCode()
        return result
    }
}