package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.types.Scope

// todo this is very special...
//  idk if we even should have a SimpleYield, or if we should "simplify" it in-place
class SimpleYield(
    field: SimpleField,
    val continueBlock: SimpleBlock,
    scope: Scope, origin: Int,
) : SimpleExit(field, scope, origin) {
    override val returnType: ReturnType get() = ReturnType.YIELD
}