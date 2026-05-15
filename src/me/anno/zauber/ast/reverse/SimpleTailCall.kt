package me.anno.zauber.ast.reverse

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.interpreting.BlockReturn

/**
 * jumps to another node
 * */
class SimpleTailCall(val toBeCalled: SimpleBlock) :
    SimpleInstruction(root, -1) {

    override fun execute(): BlockReturn? {
        TODO("Not yet implemented")
    }
}