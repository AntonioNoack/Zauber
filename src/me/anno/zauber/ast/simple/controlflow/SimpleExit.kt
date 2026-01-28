package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

abstract class SimpleExit(val field: SimpleField, scope: Scope, origin: Int) : SimpleInstruction(scope, origin) {

    abstract val returnType : ReturnType

    override fun toString(): String {
        return "${returnType.symbol} $field"
    }

    override fun execute(runtime: Runtime): BlockReturn {
        val value = runtime[field]
        return BlockReturn(returnType, value)
    }
}