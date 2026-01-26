package me.anno.zauber.ast.simple

import me.anno.zauber.types.Scope

class SimpleGraph {

    var numFields = 0
    val blocks = ArrayList<SimpleBlock>()
    val startBlock = SimpleBlock(this)

    val continueLabels = HashMap<String?, SimpleBlock>()
    val breakLabels = HashMap<String?, SimpleBlock>()

    val catchHandlers = HashMap<Scope, SimpleCatchHandler>()
    val finallyBlocks = ArrayList<SimpleFinallyBlock>()

    init {
        startBlock.isEntryPoint = true
        blocks.add(startBlock)
    }

    fun addBlock(): SimpleBlock {
        val block = SimpleBlock(this)
        blocks.add(block)
        return block
    }

}