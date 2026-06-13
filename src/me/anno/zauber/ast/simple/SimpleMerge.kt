package me.anno.zauber.ast.simple

import me.anno.utils.StringStyles.bold
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.scope.Scope

/**
 * Merges the result of an if and an else into one value.
 * This is equivalent to LLVM's phi instruction.
 * */
class SimpleMerge(
    val dst: SimpleField,
    var ifField: SimpleField,
    var elseField: SimpleField,
    scope: Scope, origin: Long
) : SimpleInstruction(scope, origin) {

    init {
        while (true) {
            val ifMergeInfo = ifField.mergeInfo ?: break
            ifField = ifMergeInfo.dst
        }

        while (true) {
            val elseMergeInfo = elseField.mergeInfo ?: break
            elseField = elseMergeInfo.dst
        }

        ifField.mergeInfo = this
        elseField.mergeInfo = this
    }

    fun other(field: SimpleField): SimpleField {
        check(field == ifField || field == elseField)
        return if (field == ifField) elseField else ifField
    }

    override fun execute(): BlockReturn? = null

    override fun hasInput(field: SimpleField): Boolean = ifField == field || elseField == field

    override fun toString(): String = "$dst = ${bold("Merge")}($ifField,$elseField)"

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleMerge(
            src.cloned(this.dst, dst),
            src.cloned(ifField, dst),
            src.cloned(elseField, dst),
            scope, origin
        )
    }

}