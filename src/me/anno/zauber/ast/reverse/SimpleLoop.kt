package me.anno.zauber.ast.reverse

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleNode
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime

class SimpleLoop(val condition: SimpleField, val body: SimpleNode) :
    SimpleInstruction(root, -1) {

    override fun execute(runtime: Runtime): BlockReturn? {
        TODO("Not yet implemented")
    }
}