package me.anno.zauber.ast.rich.expression

import me.anno.zauber.ast.rich.expression.resolved.ThisExpression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.impl.ClassType

class TypeExpression(val type: Type, scope: Scope, origin: Int) :
    Expression(scope, origin) {

    override fun toStringImpl(depth: Int): String = type.toString()
    override fun clone(scope: Scope) = TypeExpression(type, scope, origin)
    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean = false
    override fun needsBackingField(methodScope: Scope): Boolean = false

    override fun resolveReturnType(context: ResolutionContext): Type = type // only if it is an object or package...
    // todo throws and yields that of the constructor...

    override fun splitsScope(): Boolean = false
    override fun isResolved(): Boolean = false
    override fun forEachExpression(callback: (Expression) -> Unit) {}
    override fun resolveImpl(context: ResolutionContext): Expression {
        val clazz = type as? ClassType
            ?: throw IllegalStateException("Implement $this with ${type.javaClass.simpleName}")
        return ThisExpression(clazz.clazz, scope, origin)
    }
}