package me.anno.zauber.ast.reverse

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.interpreting.BlockReturn

class SimpleLoop(val condition: SimpleField, val body: SimpleBlock) :
    SimpleInstruction(root, -1) {

    override fun execute(): BlockReturn? {
        TODO("Not yet implemented")
    }
}