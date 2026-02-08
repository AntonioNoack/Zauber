package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

class TryCatchBlock(
    val tryBody: Expression, val catches: List<Catch>,
    val finally: Expression?
) : Expression(tryBody.scope, tryBody.origin) {

    override fun resolveType(context: ResolutionContext): Type {
        val bodyType = TypeResolution.resolveType(context, tryBody)
        val catchTypes = catches.map {
            TypeResolution.resolveType(context, it.body)
        }.reduceOrNull { a, b -> unionTypes(a, b) }
        return if (catchTypes == null) bodyType
        else unionTypes(bodyType, catchTypes)
    }

    override fun hasLambdaOrUnknownGenericsType(context: ResolutionContext): Boolean {
        return tryBody.hasLambdaOrUnknownGenericsType(context) ||
                catches.any { it.body.hasLambdaOrUnknownGenericsType(context) }
    }

    override fun needsBackingField(methodScope: Scope): Boolean {
        return tryBody.needsBackingField(methodScope) ||
                catches.any { it.body.needsBackingField(methodScope) } ||
                finally?.needsBackingField(methodScope) == true
    }

    // already a split on its own, or is it?
    override fun splitsScope(): Boolean = false

    override fun clone(scope: Scope) = TryCatchBlock(tryBody.clone(scope), catches.map {
        Catch(it.param.clone(it.param.scope /* I don't think we should override this */), it.body.clone(scope))
    }, finally?.clone(scope))

    override fun isResolved(): Boolean = tryBody.isResolved() &&
            catches.all { it.param.type.isResolved() && it.body.isResolved() } &&
            (finally == null || finally.isResolved())

    override fun resolveImpl(context: ResolutionContext): Expression {
        return TryCatchBlock(tryBody.resolve(context), catches.map {
            Catch(it.param, it.body.resolve(context))
        }, finally?.resolve(context))
    }

    override fun toStringImpl(depth: Int): String {
        val builder = StringBuilder()
        builder.append("try { ").append(tryBody).append(" }")
        for (catch in catches) {
            builder.append(" catch(${catch.param}) { ")
                .append(catch.body)
                .append(" }")
        }
        if (finally != null) {
            builder.append(" finally { ")
                .append(finally).append(" }")
        }
        return builder.toString()
    }

    override fun forEachExpression(callback: (Expression) -> Unit) {
        callback(tryBody)
        for (catch in catches) {
            callback(catch.body)
        }
        if (finally != null) callback(finally)
    }

}