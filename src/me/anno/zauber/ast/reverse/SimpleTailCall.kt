package me.anno.zauber.ast.reverse

import me.anno.utils.StringStyles.bold
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction

/**
 * jumps to another node
 * */
class SimpleTailCall(val toBeCalled: SimpleBlock) :
    SimpleInstruction(root, -1) {

    override fun execute() = throw NotImplementedError("Not meant to be executed")

    override fun toString(): String {
        return "${bold("SimpleTailCall")}[${toBeCalled.idStr()}]"
    }

    override fun hasInput(field: SimpleField): Boolean = false

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleTailCall(src.cloned(toBeCalled, dst))
    }

}