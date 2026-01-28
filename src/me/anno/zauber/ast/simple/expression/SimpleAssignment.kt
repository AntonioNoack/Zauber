package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.types.Scope

abstract class SimpleAssignment(val dst: SimpleField, scope: Scope, origin: Int) :
    SimpleInstruction(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(AssignmentExpression::class)
    }

    override fun execute(runtime: Runtime): BlockReturn? {
        val value = eval(runtime)
        return if (value.type == ReturnType.VALUE) {
            if (dst.numReads > 0) {
                if (LOGGER.isDebugEnabled) LOGGER.debug("$dst is now ${value.instance} by $this (${javaClass.simpleName})")
                runtime[dst] = value.instance
            }
            null
        } else value
    }

    abstract fun eval(runtime: Runtime): BlockReturn

}