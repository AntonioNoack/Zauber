package me.anno.zauber.ast.simple

class SimpleGraph {

    var numFields = 0
    val entry = SimpleBlock(this)
    val blocks = ArrayList<SimpleBlock>()

    init {
        blocks.add(entry)
    }

    fun addBlock(): SimpleBlock {
        val block = SimpleBlock(this)
        blocks.add(block)
        return block
    }

}