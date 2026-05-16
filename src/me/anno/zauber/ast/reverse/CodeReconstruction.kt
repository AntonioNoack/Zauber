package me.anno.zauber.ast.reverse

import me.anno.zauber.ast.reverse.GraphToClass.convertGraphToClass
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph

/**
 * Converts an arbitrary graph back into if-else and while-blocks.
 *
 * todo use this to split methods with yield or complex catches into many sub-functions (and trivial control-flow)
 * */
object CodeReconstruction {

    fun createCodeFromGraph(graph: SimpleGraph) {

        if (isSolved(graph)) return

        while (!isSolved(graph)) {
            if (simplifySequence(graph)) continue
            if (simplifyBranch(graph)) continue
            if (simplifyLoop(graph)) continue
            if (breakAtStrongestKnot(graph)) continue
            break
        }

        return convertGraphToClass(graph)
    }

    private fun simplifySequence(graph: SimpleGraph): Boolean {
        var changed = false
        for (i in graph.blocks.indices) {
            val curr = graph.blocks[i]
            val next = curr.nextBranch
            if (!curr.isBranch &&
                next != null && next.isOnlyInput(curr)
            ) {
                curr.instructions.addAll(next.instructions)
                curr.ifBranch = next.ifBranch
                curr.elseBranch = next.elseBranch
                curr.branchCondition = next.branchCondition
                next.clear()
                changed = true
            }
        }
        return changed
    }

    private fun simplifyBranch(graph: SimpleGraph): Boolean {
        var changed = false
        for (i in graph.blocks.indices) {
            val curr = graph.blocks[i]
            val nextT = curr.ifBranch
            val nextF = curr.elseBranch
            val after = nextT?.nextBranch
            if (curr.isBranch &&
                nextT != null && nextT.isOnlyInput(curr) &&
                nextF != null && nextF.isOnlyInput(curr) &&
                nextT.isOnlyOutput(after) &&
                nextF.isOnlyOutput(after) &&
                after?.isEntryPoint != true
            ) {
                curr.instructions.add(SimpleBranch(curr.branchCondition!!, nextT, nextF))
                nextT.removeLinks()
                nextF.removeLinks()
                curr.removeLinks()
                curr.nextBranch = after
                changed = true
            }
        }
        return changed
    }

    private fun simplifyLoop(graph: SimpleGraph): Boolean {
        for (i in graph.blocks.indices) {
            val curr = graph.blocks[i]
            val nextT = curr.ifBranch
            val nextF = curr.elseBranch
            val after = nextF?.nextBranch
            if (curr.isBranch &&
                nextT != null && nextT.isOnlyInput(curr) &&
                nextF != null && nextF.isOnlyInput(curr) &&
                // todo check this condition...
                nextT.isOnlyOutput(curr) &&
                after?.isEntryPoint != true
            ) {
                TODO("rev-simplify loop")
                return true
            }
        }
        return false
    }

    /**
     * breaks the graph on the block with the most inputs in hope of making more branches possible
     * */
    private fun breakAtStrongestKnot(graph: SimpleGraph): Boolean {
        val bestBlock = graph.blocks
            //.filter { !it.isEntryPoint }
            .filter { it.inputBlocks.size > 1 }
            .maxByOrNull { it.inputBlocks.size }
            ?: return false

        bestBlock.isEntryPoint = true
        val inputNodes = ArrayList(bestBlock.inputBlocks)
        for (inputNode in inputNodes) {
            if (inputNode.isBranch) {
                if (inputNode.ifBranch == bestBlock) {
                    inputNode.ifBranch = graph.createTailCall(bestBlock)
                } else {
                    check(inputNode.elseBranch == bestBlock) { "Expected elseBranch to be bestNode" }
                    inputNode.elseBranch = graph.createTailCall(bestBlock)
                }
            } else {
                check(inputNode.nextBranch == bestBlock)
                inputNode.ifBranch = null
                inputNode.elseBranch = null
                inputNode.branchCondition = null
                inputNode.instructions.add(SimpleTailCall(bestBlock))
            }
        }
        return true
    }

    private fun SimpleGraph.createTailCall(target: SimpleBlock): SimpleBlock {
        val node = addBlock()
        node.instructions.add(SimpleTailCall(target))
        return node
    }

    private fun isSolved(graph: SimpleGraph): Boolean {
        return graph.blocks.all {
            it.branchCondition == null &&
                    it.ifBranch == null &&
                    it.elseBranch == null
        }
    }
}