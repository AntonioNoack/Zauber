package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.types.Scope

class SimpleReturn(field: SimpleField, scope: Scope, origin: Int) : SimpleExit(field, scope, origin) {
    override val returnType: ReturnType get() = ReturnType.RETURN
}