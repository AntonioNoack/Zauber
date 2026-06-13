package me.anno.zauber.ast.reverse

import me.anno.utils.StringStyles.bold
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction

class SimpleBranch(val condition: SimpleField, val ifTrue: SimpleBlock, val ifFalse: SimpleBlock?) :
    SimpleInstruction(root, -1) {

    override fun execute() = throw NotImplementedError("Not meant to be executed")

    override fun toString(): String {
        return if (ifFalse != null) {
            "${bold("SimpleBranch")}($condition ? ${ifTrue.str()} : ${ifFalse.str()})"
        } else {
            "${bold("SimpleBranch")}($condition ? ${ifTrue.str()})"
        }
    }

    override fun hasInput(field: SimpleField): Boolean {
        return field == condition
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleBranch(
            src.cloned(condition, dst),
            src.cloned(ifTrue, dst),
            src.cloned1(ifFalse, dst)
        )
    }
}