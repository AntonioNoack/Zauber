package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope

/**
 * Merges the result of an if and an else into one value.
 * This is equivalent to LLVM's phi instruction.
 * */
class SimpleMerge(
    val dst: SimpleField,
    val ifField: SimpleField,
    val elseField: SimpleField,
    scope: Scope, origin: Int
) : SimpleInstruction(scope, origin) {

    init {
        // println("Merge: $this")
        // if so, that's fine, at least from Interpreter perspective; from LLVM maybe not
        val ifMergeInfo = ifField.mergeInfo
        val elseMergeInfo = elseField.mergeInfo
        if (ifMergeInfo != null) {
            check(
                ifMergeInfo.other(ifField) == elseField
                        || ifMergeInfo.dst == elseField
            ) {
                "Weird merge#1: merge($ifField->${ifField.mergeInfo}, $elseField->${elseField.mergeInfo})"
            }
        }
        check(elseMergeInfo == null || elseMergeInfo.other(elseField) == ifField) {
            "Weird merge#2"
        }
        // check(ifField.mergeInfo == null) { "IfField is merged twice? $ifField -> ${ifField.mergeInfo} + $this" }
        // check(elseField.mergeInfo == null) { "ElseField is merged twice? $elseField -> ${elseField.mergeInfo} + $this" }
        ifField.mergeInfo = this
        elseField.mergeInfo = this
    }

    fun other(field: SimpleField): SimpleField {
        check(field == ifField || field == elseField)
        return if (field == ifField) elseField else ifField
    }

    override fun execute(runtime: Runtime): BlockReturn? = null

    override fun toString(): String = "$dst = Merge($ifField,$elseField)"
}