package me.anno.zauber.ast.reverse

import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleNode

/**
 * Converts an arbitrary graph back into if-else and while-blocks.
 *
 * todo use this to split methods with yield or complex catches into many sub-functions (and trivial control-flow)
 * */
object CodeReconstruction {

    fun createCodeFromGraph(graph: SimpleGraph) {
        while (!isSolved(graph)) {
            if (simplifySequence(graph)) continue
            if (simplifyBranch(graph)) continue
            if (simplifyLoop(graph)) continue
            if (breakAtStrongestKnot(graph)) continue
            break
        }
        check(isSolved(graph))
    }

    private fun simplifySequence(graph: SimpleGraph): Boolean {
        var changed = false
        for (i in graph.nodes.indices) {
            val curr = graph.nodes[i]
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
        for (i in graph.nodes.indices) {
            val curr = graph.nodes[i]
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
        for (i in graph.nodes.indices) {
            val curr = graph.nodes[i]
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

    private fun breakAtStrongestKnot(graph: SimpleGraph): Boolean {
        val bestNode = graph.nodes
            .filter { !it.isEntryPoint }
            .maxBy { it.inputNodes.size }

        bestNode.isEntryPoint = true
        for (inputNode in bestNode.inputNodes) {
            if (inputNode.isBranch) {
                if (inputNode.ifBranch == bestNode) {
                    inputNode.ifBranch = graph.createTailCall(bestNode)
                } else {
                    check(inputNode.elseBranch == bestNode) { "Expected elseBranch to be bestNode" }
                    inputNode.elseBranch = graph.createTailCall(bestNode)
                }
            } else {
                inputNode.ifBranch = null
                inputNode.elseBranch = null
                inputNode.branchCondition = null
                inputNode.instructions.add(SimpleTailCall(bestNode))
            }
        }
        return true
    }

    private fun SimpleGraph.createTailCall(target: SimpleNode): SimpleNode {
        val node = addNode()
        node.instructions.add(SimpleTailCall(target))
        return node
    }

    private fun isSolved(graph: SimpleGraph): Boolean {
        return graph.nodes.all {
            it.branchCondition == null &&
                    it.ifBranch == null &&
                    it.elseBranch == null
        }
    }
}