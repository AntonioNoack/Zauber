package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.expression.DynamicMacroExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.expansion.Macro.evaluateMacroNow
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope

/**
 * simple-expression that cannot be compiled, only executed,
 * because it calls a macro;
 *
 * should only be found inside macros
 * */
class SimpleDynamicMacro(
    dst: SimpleField,
    val original: DynamicMacroExpression,
    val self: SimpleField,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleCallable(dst, original.method.specialization, scope, origin) {

    val method get() = original.method
    val methodName get() = method.resolved.name

    val imports get() = original.imports
    val generics get() = original.generics

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append(dst).append(" = ")
            .append(self).append('[').append(method.selfType).append("].")
            .append(methodName).append('!')
            .append(valueParameters.joinToString(", ", "(", ")"))
        val ot = onThrown
        if (ot != null) {
            builder.append(" throws b").append(ot.block.blockId)
                .append("(%").append(ot.value.id).append(')')
        }
        return builder.toString()
    }

    override fun eval(): BlockReturn {
        val runtime = runtime
        val self = runtime[self]
        if (runtime.isNull(self)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }

        val expression = evaluateMacroNow(method, valueParameters.map { runtime[it] }, imports, generics, original.scope, origin)
        return self.evaluateExpressionUnsafe(expression, Flags.NONE, method.returnType)
    }

}