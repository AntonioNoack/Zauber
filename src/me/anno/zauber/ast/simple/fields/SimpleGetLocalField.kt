package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

class SimpleGetLocalField(
    dst: SimpleField,
    override val field: LocalField,
    scope: Scope, origin: Long
) : SimpleAssignment(dst, scope, origin), SimpleGSetLocalField {

    override fun execute(): BlockReturn? {
        // cannot crash at runtime (if ASTSimplified correctly) -> past-path
        val runtime = runtime
        runtime[dst] = runtime.getCall().localFields[field.id]
            ?: error("Missing local field #${field.id}")
        return null
    }

    override fun toString(): String {
        return "$dst = " +
                style("local#${field.id}", YELLOW) +
                style("\"${field.name}\"", GREEN)
    }

    override fun clone(src: SimpleGraph, dst: SimpleGraph): SimpleInstruction {
        return SimpleGetLocalField(
            src.cloned(this.dst, dst),
            src.cloned(field, dst),
            scope, origin
        )
    }

}