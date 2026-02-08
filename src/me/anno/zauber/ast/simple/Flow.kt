package me.anno.zauber.ast.simple

data class Flow(val value: SimpleField, val block: SimpleNode) {
    override fun toString(): String {
        return "Flow(value=$value, block=${block.blockId})"
    }
}