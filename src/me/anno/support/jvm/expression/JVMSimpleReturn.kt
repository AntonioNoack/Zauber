package me.anno.support.jvm.expression

import me.anno.utils.StringStyles.ORANGE
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleReturn(val value: JVMSimpleField, scope: Scope, origin: Long) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = Types.Nothing
    override fun toStringImpl(depth: Int): String = "${style("return", ORANGE)} $value"
    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        var value = value.toSimple(block0)

        val expectedReturnType = block0.graph.expectedReturnType
        var actualReturnType = value.type

        if (actualReturnType == Types.Int && expectedReturnType != actualReturnType) {
            val methodName = when (expectedReturnType) {
                Types.Boolean -> "toBoolean"
                Types.Byte -> "toByte"
                Types.Short -> "toShort"
                Types.Char -> "toChar"
                else -> null
            }
            if (methodName != null) {
                val method = Types.Int.clazz[ScopeInitType.AFTER_DISCOVERY]
                    .methods0.firstOrNull { it.name == methodName }
                    ?: error("Missing method $methodName in ${Types.Int}")
                val spec = Specialization.fromSimple(method.memberScope)
                val dst = block0.field(expectedReturnType)
                val call = SimpleCall(dst, method, value, null, spec, emptyList(), scope, origin)
                block0.add(call)
                actualReturnType = expectedReturnType
                value = dst
            }
        }

        check(isSubTypeOf(expectedReturnType, actualReturnType)) {
            val method = block0.graph.method
            "Expected return value in $method " +
                    "to match $expectedReturnType, got $actualReturnType\n" +
                    "  spec: ${block0.graph.method}\n" +
                    "  at ${resolveOrigin(origin)}"
        }

        return flow0.joinReturnNoValue(value.use(), block0)
    }
}