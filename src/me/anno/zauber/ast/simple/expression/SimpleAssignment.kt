package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope

abstract class SimpleAssignment(val dst: SimpleField, scope: Scope, origin: Int) :
    SimpleInstruction(scope, origin) {

    companion object {
        private val LOGGER = LogManager.getLogger(AssignmentExpression::class)
    }

    override fun execute(): BlockReturn? {
        val value = eval()
        return if (value.type == ReturnType.VALUE || value.type == ReturnType.RETURN) {
            if (dst.numReads > 0) {
                if (LOGGER.isDebugEnabled) LOGGER.debug("$dst is now ${value.value} by $this (${javaClass.simpleName})")
                runtime[dst] = value.value
            }
            null
        } else value
    }

    abstract fun eval(): BlockReturn

}