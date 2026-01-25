package me.anno.zauber.ast.simple

import me.anno.zauber.types.Scope

class SimpleGraph(scope: Scope, origin: Int) {

    var numFields = 0
    val blocks = ArrayList<SimpleBlock>()
    val startBlock = SimpleBlock(this, scope, origin)

    val continueLabels = HashMap<String?, SimpleBlock>()
    val breakLabels = HashMap<String?, SimpleBlock>()

    val catchHandlers = HashMap<Scope, SimpleCatchHandler>()
    val finallyBlocks = ArrayList<SimpleFinallyBlock>()

    init {
        startBlock.isEntryPoint = true
        blocks.add(startBlock)
    }

    fun addBlock(scope: Scope, origin: Int): SimpleBlock {
        val block = SimpleBlock(this, scope, origin)
        blocks.add(block)
        return block
    }

}