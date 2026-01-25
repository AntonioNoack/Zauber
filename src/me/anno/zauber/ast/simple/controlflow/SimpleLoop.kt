package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

/**
 * while(true) { body }
 * */
class SimpleLoop(val body: SimpleBlock, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin) {

    override fun toString(): String {
        return "while(true) { $body }"
    }

    override fun execute(runtime: Runtime): BlockReturn? {
        return runtime.executeBlock(body)
    }
}