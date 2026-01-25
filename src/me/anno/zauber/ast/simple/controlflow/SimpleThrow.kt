package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.types.Scope

class SimpleThrow(field: SimpleField, scope: Scope, origin: Int) : SimpleExit(field, scope, origin) {
    override val returnType: ReturnType get() = ReturnType.THROW
}