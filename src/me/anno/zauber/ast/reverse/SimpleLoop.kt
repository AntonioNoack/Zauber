package me.anno.zauber.ast.reverse

import me.anno.utils.StringStyles.bold
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction

class SimpleLoop private constructor(
    val condition: SimpleField?,
    val conditionBlock: SimpleBlock?,
    val negate: Boolean,
    val body: SimpleBlock
) : SimpleInstruction(root, -1) {

    constructor(conditionBlock: SimpleBlock, condition: SimpleField, negate: Boolean, body: SimpleBlock) :
            this(condition, conditionBlock, negate, body)

    constructor(body: SimpleBlock) :
            this(null, null, false, body)

    init {
        check(conditionBlock != null || !negate) {
            "Loop without condition, and negate=true is useless"
        }
    }

    override fun execute() = throw NotImplementedError("Not meant to be executed")

    override fun toString(): String {
        return if (condition != null) {
            "${bold("SimpleLoop")}(${conditionBlock!!.str()}|$condition, negate=$negate ? ${body.str()})"
        } else {
            "${bold("SimpleLoop")}(${body.str()})"
        }
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleLoop(
            src.cloned1(condition, dst),
            src.cloned1(conditionBlock, dst),
            negate, src.cloned(body, dst),
        )
    }

}