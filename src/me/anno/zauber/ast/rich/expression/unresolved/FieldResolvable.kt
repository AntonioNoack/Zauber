package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedField

interface FieldResolvable {
    fun resolveField(context: ResolutionContext): ResolvedField?

    fun onMissingField(): Nothing {
        throw IllegalStateException("Unable to resolve field for $this")
    }
}