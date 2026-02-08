package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.MethodLike

class SimpleGraph(val method: MethodLike) {

    var numFields = 0
    val nodes = ArrayList<SimpleNode>()
    val startBlock = SimpleNode(this)

    val continueLabels = HashMap<String?, SimpleNode>()
    val breakLabels = HashMap<String?, SimpleNode>()

    init {
        startBlock.isEntryPoint = true
        nodes.add(startBlock)
    }

    fun addNode(): SimpleNode {
        val node = SimpleNode(this)
        nodes.add(node)
        return node
    }

    override fun toString(): String {
        return "Graph[${nodes.size} nodes, $numFields fields]\n" +
                nodes.joinToString("\n") { it.toString() }
    }
}