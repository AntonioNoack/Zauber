package me.anno.support.jvm.expression

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.fields.SimpleGetClassField
import me.anno.zauber.ast.simple.fields.SimpleGetObject
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type

class JVMSimpleGetClassField(
    val dst: JVMSimpleField, val self: JVMSimpleField, val field: Field,
    scope: Scope, origin: Long
) : JVMSimpleExpr(scope, origin) {

    override fun resolveValueType(context: ResolutionContext): Type = dst.type
    override fun toStringImpl(depth: Int): String = "$dst = $self.$field"

    override fun simplify(
        context: ResolutionContext,
        block0: SimpleBlock,
        flow0: FlowResult,
        needsValue: Boolean,
        contextExpr: Expression?
    ): FlowResult {

        val dst = dst.toSimple(block0)
        if (field.isObjectInstance()) {
            val value = SimpleGetObject(dst, field.ownerScope, scope, origin)
            block0.add(value)
            return flow0.withValue(dst)
        }

        val self = self.toSimple(block0)
        val specialization = context.specialization.withScopeUnknownIfMissing(field.fieldScope)
        val getter = SimpleGetClassField(
            dst, self.use(), field,
            specialization, scope, origin
        )

        block0.add(getter)
        return flow0.withValue(dst)
    }

}