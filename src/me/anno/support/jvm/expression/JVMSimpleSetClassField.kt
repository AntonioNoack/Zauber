package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleSetClassField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

class JVMSimpleSetClassField(
    val self: JVMSimpleField, val field: Field, val value: JVMSimpleField,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {
    override fun resolveValueType(context: ResolutionContext): Type = Types.Unit
    override fun toStringImpl(depth: Int): String = "$self.$field = $value"
    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val self = self.toSimple(block0)
        val value = value.toSimple(block0)

        val specialization = context.specialization.withScope(field.fieldScope)
        val getter = SimpleSetClassField(
            self.use(), field, value.use(),
            specialization, scope, origin
        )

        block0.add(getter)
        return flow0.withValue(value) // in theory unit, but we don't use it anyway
    }
}