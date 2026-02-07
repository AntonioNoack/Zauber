package me.anno.zauber.ast.simple

class SimpleGraph {

    var numFields = 0
    val nodes = ArrayList<SimpleNode>()
    val startBlock = SimpleNode(this)

    val continueLabels = HashMap<String?, SimpleNode>()
    val breakLabels = HashMap<String?, SimpleNode>()

    /**
     * throwable-handler
     * */
    var onThrow: SimpleNode? = null

    /**
     * exit-handler
     * */
    var onReturn: SimpleNode? = null

    fun <R> pushTryFinally(onThrow: SimpleNode?, onReturn: SimpleNode?, runnable: () -> R): R {
        val oldReturn = this.onReturn
        val oldThrow = this.onReturn
        if (onReturn != null) {
            onReturn.onReturn = oldReturn
            this.onReturn = onReturn
        }
        if (onThrow != null) {
            onThrow.onThrow = oldThrow
            this.onThrow = onThrow
        }
        return try {
            runnable()
        } finally {
            this.onReturn = oldReturn
            this.onThrow = oldThrow
        }
    }

    init {
        startBlock.isEntryPoint = true
        nodes.add(startBlock)
    }

    fun addNode(): SimpleNode {
        val node = SimpleNode(this)
        node.onReturn = this.onReturn
        node.onThrow = this.onThrow
        nodes.add(node)
        return node
    }

    override fun toString(): String {
        return "Graph[${nodes.size} nodes, $numFields fields]\n" +
                nodes.joinToString("\n") { it.toString() }
    }

}