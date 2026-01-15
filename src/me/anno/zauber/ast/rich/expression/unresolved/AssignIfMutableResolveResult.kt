package me.anno.zauber.ast.rich.expression.unresolved

import me.anno.zauber.typeresolution.members.ResolvedField
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type

data class AssignIfMutableResolveResult(
    val leftType: Type,
    val rightType: Type,
    val needsFieldAssignment: ResolvedField?,
    val neededCall: ResolvedMethod
)