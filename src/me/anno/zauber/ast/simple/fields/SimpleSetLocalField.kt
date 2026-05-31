package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleSetLocalField(
    override val field: LocalField,
    var value: SimpleField, scope: Scope, origin: Long
) : SimpleInstruction(scope, origin), SimpleGSetLocalField {

    override fun execute(): BlockReturn? {
        val runtime = runtime
        runtime.getCall().localFields[field.id] = runtime[value]
        return null
    }

    override fun toString(): String {
        return "${style("local#${field.id}", YELLOW)} = $value"
    }

}