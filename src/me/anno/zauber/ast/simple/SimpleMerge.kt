package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

/**
 * Merges the result of an if and an else into one value.
 * This is equivalent to LLVM's phi instruction.
 * */
class SimpleMerge(
    val dst: SimpleField,
    ifField: SimpleField,
    elseField: SimpleField,
    scope: Scope, origin: Int
) : SimpleInstruction(scope, origin) {

    init {
        check(ifField.mergeInfo == null) { "IfField is merged twice? $ifField" }
        check(elseField.mergeInfo == null) { "ElseField is merged twice? $elseField" }
        ifField.mergeInfo = this
        elseField.mergeInfo = this
    }

    override fun execute(runtime: Runtime): BlockReturn? = null
}