package me.anno.zauber.interpreting

data class BlockReturn(val type: ReturnType, val value: Instance) {
    fun retToVal(): BlockReturn {
        return if (type == ReturnType.RETURN) BlockReturn(ReturnType.VALUE, value) else this
    }
}